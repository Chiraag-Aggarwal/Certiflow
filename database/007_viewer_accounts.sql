-- Apply after 006_public_sharing.sql. Existing accounts and the administrator
-- bootstrap are unchanged. Account roles are selected from the server profile.
begin;

alter table public.profiles drop constraint profiles_role_check;
alter table public.profiles add constraint profiles_role_check
    check (role in ('STUDENT','VIEWER','TEACHER','ADMIN'));

-- Public signup can request only the additional low-privilege Viewer role.
-- Untrusted role/identity metadata never grants teacher or administrator access.
-- This trigger runs only on account creation: editing auth metadata afterwards
-- does not change the trusted profile role.
create or replace function public.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles(id, full_name, email, role, department, student_number)
    values (new.id, coalesce(nullif(btrim(new.raw_user_meta_data->>'full_name'),''), new.email),
        new.email, case when new.raw_user_meta_data->>'account_type' = 'VIEWER' then 'VIEWER' else 'STUDENT' end,
        coalesce(new.raw_user_meta_data->>'department',''),
        coalesce(new.raw_user_meta_data->>'student_number',''));
    return new;
end;
$$;
revoke all on function public.create_profile() from public, anon, authenticated;

-- Keep the existing confirmed-administrator guard and protect all ADMIN accounts.
create or replace function public.set_account_role(target_id uuid, new_role text)
returns public.profiles language plpgsql security definer set search_path = '' as $$
declare result public.profiles;
begin
    if not public.is_admin() then raise exception 'Only administrators can assign account access.' using errcode = '42501'; end if;
    if target_id = auth.uid() or new_role is null or new_role not in ('STUDENT','VIEWER','TEACHER') then
        raise exception 'Choose another verified account and Student, Viewer or Teacher access.' using errcode = '42501';
    end if;
    update public.profiles p set role = new_role
    where p.id = target_id and p.role <> 'ADMIN'
        and exists(select 1 from auth.users u where u.id = p.id and u.email_confirmed_at is not null)
    returning p.* into result;
    if result.id is null then raise exception 'This account cannot be changed. It must be verified and cannot be an administrator.' using errcode = '42501'; end if;
    return result;
end;
$$;
revoke all on function public.set_account_role(uuid,text) from public, anon, authenticated;
grant execute on function public.set_account_role(uuid,text) to authenticated;

-- Existing insert policies already require STUDENT. Also keep a former student
-- switched to VIEWER from editing old submissions. Preserve the previous owner
-- permissions for STUDENT, TEACHER and ADMIN, and keep all original read scopes.
alter policy projects_update on public.portfolio_projects
    using (student_id = (select auth.uid()) and exists(select 1 from public.profiles
        where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN')))
    with check (student_id = (select auth.uid()) and exists(select 1 from public.profiles
        where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN')));
alter policy projects_delete on public.portfolio_projects
    using (student_id = (select auth.uid()) and exists(select 1 from public.profiles
        where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN')));
alter policy portfolios_update on public.portfolios
    using (student_id = (select auth.uid()) and exists(select 1 from public.profiles
        where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN')))
    with check (student_id = (select auth.uid()) and exists(select 1 from public.profiles
        where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN')));
alter policy portfolios_delete on public.portfolios
    using (student_id = (select auth.uid()) and exists(select 1 from public.profiles
        where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN')));
alter policy portfolio_files_cleanup on storage.objects
    using (bucket_id = 'portfolios' and (storage.foldername(name))[1] = (select auth.uid())::text
        and exists(select 1 from public.profiles where id = (select auth.uid()) and role in ('STUDENT','TEACHER','ADMIN'))
        and not exists(select 1 from public.portfolios where stored_name = name));

-- Existing staff helpers and recipient validation use explicit TEACHER/ADMIN
-- allowlists, not "not student". Viewers therefore cannot review requests, read
-- the private roster or choose another viewer as a submission recipient.
-- Confirmed viewers can chat only with confirmed staff. Historic participant
-- conversations remain private and readable; chat_inbox uses the same rule.
create or replace function public.validate_message() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if new.sender_id is distinct from auth.uid() or new.sender_id = new.recipient_id
        or not exists(select 1 from public.profiles s join auth.users su on su.id = s.id
            cross join public.profiles r join auth.users ru on ru.id = r.id
            where s.id = new.sender_id and r.id = new.recipient_id
                and su.email_confirmed_at is not null and ru.email_confirmed_at is not null
                and (s.role in ('TEACHER','ADMIN') or r.role in ('TEACHER','ADMIN'))) then
        raise exception 'Choose a verified contact. Students and viewers can message staff; staff can message any verified account.' using errcode = '42501';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(new.sender_id::text, 0));
    if (select count(*) from public.messages where sender_id = new.sender_id and sent_at > now() - interval '1 minute') >= 30 then
        raise exception 'Please wait a minute before sending more messages.' using errcode = '23514';
    end if;
    return new;
end;
$$;
revoke all on function public.validate_message() from public, anon, authenticated;

-- A Viewer may browse opted-in community snapshots, but cannot publish or edit
-- a snapshot, even after having previously owned a student project. Unpublish
-- remains owner-authorized for every role so old links can always be revoked.
create or replace function public.publish_share(source_kind text, source_id uuid, public_title text, public_summary text,
    public_author text, public_link text, audience text, file_ids uuid[] default '{}') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare saved_id uuid; selected uuid[]; desired_audience text := audience;
begin
    if not exists(select 1 from public.profiles where id=auth.uid() and role in ('STUDENT','TEACHER','ADMIN')) then
        raise exception 'A viewer account cannot publish student submissions.' using errcode='42501';
    end if;
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

notify pgrst, 'reload schema';
commit;
