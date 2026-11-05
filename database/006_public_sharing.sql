-- Opt-in publication of a separate, curated view. Existing content stays private.
begin;

create table public.share_publications (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.profiles(id) on delete cascade,
    project_id uuid unique references public.portfolio_projects(id) on delete cascade,
    request_id uuid unique references public.requests(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 150),
    summary text not null default '' check (char_length(summary) <= 2000),
    display_name text not null check (char_length(btrim(display_name)) between 1 and 120),
    link text not null default '' check (char_length(link) <= 500 and (link = '' or link ~* '^https?://[^/@[:space:]]+([/?#][^[:space:]]*)?$')),
    audience text not null check (audience in ('COMMUNITY','LINK','BOTH')),
    token uuid not null unique default gen_random_uuid(),
    published_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((project_id is not null)::integer + (request_id is not null)::integer = 1)
);
create index share_publications_audience_date on public.share_publications(audience, published_at desc, id);
alter table public.share_publications enable row level security;
revoke all on public.share_publications from public, anon, authenticated;

create table public.share_files (
    publication_id uuid not null references public.share_publications(id) on delete cascade,
    file_id uuid not null references public.portfolios(id) on delete cascade,
    primary key(publication_id,file_id)
);
create index share_files_file on public.share_files(file_id);
alter table public.share_files enable row level security;
revoke all on public.share_files from public, anon, authenticated;

-- Only other checked functions may call this serializer. It never returns account
-- emails, institution IDs, application reasons/notes, recipients, or chat data.
create function public.share_payload(publication_id uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
    select jsonb_build_object(
        'id',s.id,'token',s.token,'source_kind',case when s.project_id is not null then 'PROJECT' else 'NOC' end,
        'title',s.title,'summary',s.summary,'display_name',s.display_name,'link',s.link,
        'audience',s.audience,'published_at',s.published_at,'updated_at',s.updated_at,
        'category',coalesce(p.kind,r.type),'status',r.status,
        'files',coalesce((select jsonb_agg(jsonb_build_object('id',f.id,'title',f.title,'file_name',f.file_name,'stored_name',f.stored_name) order by f.added_at,f.id)
            from public.share_files sf join public.portfolios f on f.id=sf.file_id
            where sf.publication_id=s.id and f.project_id=s.project_id and f.student_id=s.owner_id),'[]'::jsonb))
    from public.share_publications s
    left join public.portfolio_projects p on p.id=s.project_id
    left join public.requests r on r.id=s.request_id
    where s.id=publication_id;
$$;
revoke all on function public.share_payload(uuid) from public, anon, authenticated;

create function public.get_own_share(source_kind text, source_id uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
    select public.share_payload(s.id) from public.share_publications s
    where s.owner_id=auth.uid() and ((source_kind='PROJECT' and s.project_id=source_id) or (source_kind='NOC' and s.request_id=source_id));
$$;
revoke all on function public.get_own_share(text,uuid) from public, anon, authenticated;
grant execute on function public.get_own_share(text,uuid) to authenticated;

create function public.publish_share(source_kind text, source_id uuid, public_title text, public_summary text,
    public_author text, public_link text, audience text, file_ids uuid[] default '{}') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare saved_id uuid; selected uuid[]; desired_audience text := audience;
begin
    if auth.uid() is null then raise exception 'Sign in to publish.' using errcode='42501'; end if;
    if source_kind='PROJECT' then
        perform 1 from public.portfolio_projects p where p.id=source_id and p.student_id=auth.uid() for share;
    elsif source_kind='NOC' then
        perform 1 from public.requests r where r.id=source_id and r.student_id=auth.uid() for share;
    else raise exception 'Choose a project or NOC application.' using errcode='23514'; end if;
    if not found then raise exception 'Only the owner can publish this item.' using errcode='42501'; end if;
    if desired_audience is null or desired_audience not in ('COMMUNITY','LINK','BOTH') then raise exception 'Choose a sharing audience.' using errcode='23514'; end if;
    if public_title is null or char_length(btrim(public_title)) not between 1 and 150
        or public_author is null or char_length(btrim(public_author)) not between 1 and 120
        or char_length(coalesce(public_summary,''))>2000 or char_length(coalesce(public_link,''))>500 then
        raise exception 'Check the title, display name, summary and link lengths.' using errcode='23514';
    end if;
    if coalesce(public_link,'') <> '' and public_link !~* '^https?://[^/@[:space:]]+([/?#][^[:space:]]*)?$' then
        raise exception 'Use a full HTTP or HTTPS link without embedded credentials.' using errcode='23514';
    end if;
    selected := array(select distinct unnest(coalesce(file_ids,'{}'::uuid[])));
    if cardinality(selected)>30 or array_position(selected,null) is not null then raise exception 'Choose up to 30 files.' using errcode='23514'; end if;
    if cardinality(selected)>0 and (source_kind<>'PROJECT' or
        (select count(*) from public.portfolios f where f.id=any(selected) and f.project_id=source_id and f.student_id=auth.uid())<>cardinality(selected)) then
        raise exception 'Only files from your selected project can be published.' using errcode='42501';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||source_kind||source_id::text,6));
    select s.id into saved_id from public.share_publications s where s.owner_id=auth.uid()
        and ((source_kind='PROJECT' and s.project_id=source_id) or (source_kind='NOC' and s.request_id=source_id)) for update;
    if saved_id is null then
        insert into public.share_publications(owner_id,project_id,request_id,title,summary,display_name,link,audience)
        values(auth.uid(),case when source_kind='PROJECT' then source_id end,case when source_kind='NOC' then source_id end,
            btrim(public_title),btrim(coalesce(public_summary,'')),btrim(public_author),btrim(coalesce(public_link,'')),desired_audience)
        returning id into saved_id;
    else
        update public.share_publications s set title=btrim(public_title),summary=btrim(coalesce(public_summary,'')),
            display_name=btrim(public_author),link=btrim(coalesce(public_link,'')),audience=desired_audience,updated_at=now(),
            token=case when s.audience='COMMUNITY' and desired_audience in ('LINK','BOTH') then gen_random_uuid() else s.token end
        where s.id=saved_id;
    end if;
    delete from public.share_files sf where sf.publication_id=saved_id;
    insert into public.share_files(publication_id,file_id) select saved_id,unnest(selected);
    return public.share_payload(saved_id);
end;
$$;
revoke all on function public.publish_share(text,uuid,text,text,text,text,text,uuid[]) from public, anon, authenticated;
grant execute on function public.publish_share(text,uuid,text,text,text,text,text,uuid[]) to authenticated;

create function public.unpublish_share(source_kind text, source_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'Sign in to change sharing.' using errcode='42501'; end if;
    perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||source_kind||source_id::text,6));
    delete from public.share_publications s where s.owner_id=auth.uid()
        and ((source_kind='PROJECT' and s.project_id=source_id) or (source_kind='NOC' and s.request_id=source_id));
end;
$$;
revoke all on function public.unpublish_share(text,uuid) from public, anon, authenticated;
grant execute on function public.unpublish_share(text,uuid) to authenticated;

create function public.get_public_share(share_token uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
    select public.share_payload(s.id) from public.share_publications s
    where s.token=share_token and s.audience in ('LINK','BOTH');
$$;
revoke all on function public.get_public_share(uuid) from public, anon, authenticated;
grant execute on function public.get_public_share(uuid) to anon,authenticated;

create function public.community_shares() returns setof jsonb
language sql stable security definer set search_path = '' as $$
    select public.share_payload(s.id) from public.share_publications s
    where s.audience in ('COMMUNITY','BOTH') and exists(select 1 from public.profiles where id=auth.uid())
    order by s.published_at desc,s.id;
$$;
revoke all on function public.community_shares() from public, anon, authenticated;
grant execute on function public.community_shares() to authenticated;

-- A checked helper avoids the storage/portfolio policy-expansion cycle. Public
-- access applies only to files explicitly selected in a current publication.
create function public.can_read_published_file(object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists(select 1 from public.share_files sf
        join public.share_publications s on s.id=sf.publication_id
        join public.portfolios f on f.id=sf.file_id
        where f.stored_name=object_name and f.project_id=s.project_id and f.student_id=s.owner_id
        and (s.audience in ('LINK','BOTH') or (s.audience='COMMUNITY' and exists(select 1 from public.profiles where id=auth.uid()))));
$$;
revoke all on function public.can_read_published_file(text) from public, anon, authenticated;
grant execute on function public.can_read_published_file(text) to anon,authenticated;
create policy published_portfolio_files_read on storage.objects for select to anon,authenticated
using(bucket_id='portfolios' and storage.allow_only_operation('object.get_authenticated')
    and public.can_read_published_file(name));

notify pgrst,'reload schema';
commit;
