-- Apply once after 008. Remarks never enter public/community publications.
begin;
create table public.private_remarks (
    id uuid primary key default gen_random_uuid(),
    request_id uuid references public.requests(id) on delete cascade,
    project_id uuid references public.portfolio_projects(id) on delete cascade,
    student_id uuid not null references public.profiles(id),
    author_id uuid not null references public.profiles(id),
    author_name text not null,
    body text not null check (char_length(btrim(body)) between 1 and 2000),
    created_at timestamptz not null default now(),
    check (num_nonnulls(request_id,project_id)=1)
);
create index private_remarks_request on public.private_remarks(request_id,created_at desc,id desc);
create index private_remarks_project on public.private_remarks(project_id,created_at desc,id desc);
alter table public.private_remarks enable row level security;
revoke all on public.private_remarks from public, anon, authenticated;
grant select on public.private_remarks to authenticated;

-- A sender must still have verified staff access. A recipient must still have
-- verified Student access. VIEWER cannot retain access after a role change.
create function public.can_read_private_remark(recipient uuid, sender uuid)
returns boolean language sql stable security definer set search_path='' as $$
    select exists(select 1 from public.profiles p join auth.users u on u.id=p.id
        where p.id=auth.uid() and u.email_confirmed_at is not null and
        (p.role='ADMIN' or (p.role='STUDENT' and p.id=recipient)
            or (p.role='TEACHER' and p.id=sender)));
$$;
revoke all on function public.can_read_private_remark(uuid,uuid) from public,anon,authenticated;
grant execute on function public.can_read_private_remark(uuid,uuid) to authenticated;
create policy remarks_read on public.private_remarks for select to authenticated
using (public.can_read_private_remark(student_id,author_id));

create function public.add_private_remark(source_kind text,source_id uuid,remark_text text)
returns public.private_remarks language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); target_student uuid; sender_name text; result public.private_remarks;
begin
    -- Lock the role while checking permission and saving, so concurrent demotion
    -- cannot leave a stale staff check in use.
    select p.full_name into sender_name from public.profiles p
    join auth.users u on u.id=p.id where p.id=actor and p.role in ('TEACHER','ADMIN')
        and u.email_confirmed_at is not null for share of p;
    if sender_name is null then raise exception 'Only verified staff can send private remarks.' using errcode='42501';end if;
    if remark_text is null or char_length(btrim(remark_text)) not between 1 and 2000 then
        raise exception 'Write a remark between 1 and 2,000 characters.' using errcode='22023';end if;
    if source_kind='NOC' then
        select r.student_id into target_student from public.requests r
        where r.id=source_id and (public.is_admin() or r.recipient_id is null or r.recipient_id=actor) for share;
    elsif source_kind='PROJECT' then
        select p.student_id into target_student from public.portfolio_projects p
        where p.id=source_id and (public.is_admin() or p.recipient_id=actor) for share;
    else raise exception 'Choose an NOC or portfolio entry.' using errcode='22023';end if;
    if target_student is null then raise exception 'This item is not available for your review.' using errcode='42501';end if;
    if not exists(select 1 from public.profiles p join auth.users u on u.id=p.id
        where p.id=target_student and p.role='STUDENT' and u.email_confirmed_at is not null) then
        raise exception 'The recipient must have a verified student account.' using errcode='42501';end if;
    insert into public.private_remarks(request_id,project_id,student_id,author_id,author_name,body)
    values(case when source_kind='NOC' then source_id end,case when source_kind='PROJECT' then source_id end,
        target_student,actor,sender_name,btrim(remark_text)) returning * into result;
    return result;
end;
$$;
revoke all on function public.add_private_remark(text,uuid,text) from public,anon,authenticated;
grant execute on function public.add_private_remark(text,uuid,text) to authenticated;
notify pgrst,'reload schema';
commit;
