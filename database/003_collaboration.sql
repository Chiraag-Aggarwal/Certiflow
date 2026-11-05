-- Apply once after 001_initial.sql and 002_function_permissions.sql.
-- Existing accounts, applications, and document objects are preserved.
begin;

alter table public.profiles
    add column bio text not null default '' check (char_length(bio) <= 1000),
    add column skills text not null default '' check (char_length(skills) <= 300),
    add column programme text not null default '' check (char_length(programme) <= 120),
    add column website text not null default '' check (char_length(website) <= 500 and (website = '' or website ~* '^https?://[^[:space:]]+$')),
    add column identity_verified boolean not null default false,
    add column verified_by uuid references public.profiles(id),
    add column verified_at timestamptz,
    add constraint profiles_verification_consistent check (
        (identity_verified and verified_by is not null and verified_at is not null)
        or (not identity_verified and verified_by is null and verified_at is null));

grant update(bio, skills, programme, website) on public.profiles to authenticated;

create function public.is_admin() returns boolean language sql stable security definer
set search_path = '' as $$
    select exists(select 1 from public.profiles p join auth.users u on u.id = p.id
        where p.id = (select auth.uid()) and p.role = 'ADMIN' and u.email_confirmed_at is not null);
$$;
revoke all on function public.is_admin() from public, anon, authenticated;
grant execute on function public.is_admin() to authenticated;

create function public.reset_identity_verification() returns trigger language plpgsql
set search_path = '' as $$
begin
    if (new.full_name, new.department, new.student_number, new.email)
        is distinct from (old.full_name, old.department, old.student_number, old.email) then
        new.identity_verified := false;
        new.verified_by := null;
        new.verified_at := null;
    end if;
    return new;
end;
$$;
revoke all on function public.reset_identity_verification() from public, anon, authenticated;
create trigger certiflow_reset_verification before update on public.profiles
for each row execute function public.reset_identity_verification();

create function public.set_account_role(target_id uuid, new_role text)
returns public.profiles language plpgsql security definer set search_path = '' as $$
declare result public.profiles;
begin
    if not public.is_admin() then raise exception 'Only administrators can assign staff access.' using errcode = '42501'; end if;
    if target_id = auth.uid() or new_role is null or new_role not in ('STUDENT','TEACHER') then
        raise exception 'Choose another verified account and Student or Teacher access.' using errcode = '42501';
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

create function public.verify_identity(target_id uuid)
returns public.profiles language plpgsql security definer set search_path = '' as $$
declare result public.profiles;
begin
    if not public.is_admin() then raise exception 'Only administrators can verify profile identities.' using errcode = '42501'; end if;
    update public.profiles p set identity_verified = true, verified_by = auth.uid(), verified_at = now()
    where p.id = target_id
        and exists(select 1 from auth.users u where u.id = p.id and u.email_confirmed_at is not null)
    returning p.* into result;
    if result.id is null then raise exception 'The account must verify its email before identity review.' using errcode = '42501'; end if;
    return result;
end;
$$;
revoke all on function public.verify_identity(uuid) from public, anon, authenticated;
grant execute on function public.verify_identity(uuid) to authenticated;

-- This small directory intentionally excludes email, student IDs, and profile biographies.
create function public.contact_directory()
returns table(id uuid, full_name text, role text, department text)
language sql stable security definer set search_path = '' as $$
    select p.id, p.full_name, p.role, p.department
    from public.profiles p join auth.users u on u.id = p.id
    where auth.uid() is not null and p.id <> auth.uid() and u.email_confirmed_at is not null
        and exists(select 1 from public.profiles caller where caller.id = auth.uid())
        and (public.is_staff() or p.role in ('TEACHER','ADMIN'))
    order by lower(p.full_name), p.id;
$$;
revoke all on function public.contact_directory() from public, anon, authenticated;
grant execute on function public.contact_directory() to authenticated;

alter table public.requests add column recipient_id uuid references public.profiles(id);
alter table public.requests drop constraint requests_type_check;
alter table public.requests add constraint requests_type_check check (type in (
    'Library Clearance','Laboratory Access','Sports Equipment','Internship','Hostel Clearance',
    'Fee Clearance','Transfer Certificate','Migration Certificate','Bonafide Certificate',
    'Character Certificate','Transcript','Scholarship','Exam Permission','Event Participation',
    'Leave Application','Research Project','Other'));
grant insert(recipient_id) on public.requests to authenticated;
create index requests_recipient_order on public.requests(recipient_id, submitted_at desc, id);

-- Used by both projects and applications; the submitted UUID must name real verified staff.
create function public.validate_staff_recipient() returns trigger language plpgsql security definer
set search_path = '' as $$
begin
    if new.recipient_id is not null and not exists(
        select 1 from public.profiles p join auth.users u on u.id = p.id
        where p.id = new.recipient_id and p.role in ('TEACHER','ADMIN') and u.email_confirmed_at is not null
    ) then raise exception 'Choose a registered, email-verified teacher or administrator.' using errcode = '23514'; end if;
    return new;
end;
$$;
revoke all on function public.validate_staff_recipient() from public, anon, authenticated;
create trigger certiflow_request_recipient before insert or update of recipient_id on public.requests
for each row execute function public.validate_staff_recipient();

drop policy requests_read on public.requests;
create policy requests_read on public.requests for select to authenticated
using (student_id = (select auth.uid()) or public.is_admin()
    or (public.is_staff() and (recipient_id is null or recipient_id = (select auth.uid()))));

create or replace function public.decide_request(request_id uuid, decision text, note text default '')
returns public.requests language plpgsql security definer set search_path = '' as $$
declare result public.requests;
begin
    if not public.is_staff() then raise exception 'Only staff can review applications.' using errcode = '42501'; end if;
    if decision is null or decision not in ('APPROVED','REJECTED') then raise exception 'Choose Approve or Reject.'; end if;
    note := btrim(coalesce(note,''));
    if char_length(note) > 2000 or (decision = 'REJECTED' and note = '') then raise exception 'Enter a rejection reason of up to 2,000 characters.'; end if;
    update public.requests set status = decision, decision_note = note, decided_at = now(), decided_by = auth.uid()
    where id = request_id and status = 'PENDING'
        and (public.is_admin() or recipient_id is null or recipient_id = auth.uid())
    returning * into result;
    if result.id is null then raise exception 'This application is unavailable, assigned to another teacher, or already reviewed.' using errcode = '42501'; end if;
    return result;
end;
$$;
revoke all on function public.decide_request(uuid,text,text) from public, anon, authenticated;
grant execute on function public.decide_request(uuid,text,text) to authenticated;

create table public.portfolio_projects (
    id uuid primary key default gen_random_uuid(),
    student_id uuid not null default auth.uid() references public.profiles(id),
    title text not null check (char_length(btrim(title)) between 1 and 150),
    kind text not null default 'Project' check (kind in ('Project','Certificate','Achievement','Experience','Document')),
    description text not null default '' check (char_length(description) <= 4000),
    link text not null default '' check (char_length(link) <= 500 and (link = '' or link ~* '^https?://[^[:space:]]+$')),
    recipient_id uuid references public.profiles(id),
    created_at timestamptz not null default now()
);
create index portfolio_projects_owner_order on public.portfolio_projects(student_id, created_at desc, id);
create index portfolio_projects_recipient_order on public.portfolio_projects(recipient_id, created_at desc, id);
alter table public.portfolio_projects enable row level security;
revoke all on public.portfolio_projects from public, anon, authenticated;
grant select, delete on public.portfolio_projects to authenticated;
grant insert(title, kind, description, link, recipient_id), update(title, kind, description, link, recipient_id)
    on public.portfolio_projects to authenticated;
create policy projects_read on public.portfolio_projects for select to authenticated
using (student_id = (select auth.uid()) or public.is_admin()
    or (public.is_staff() and recipient_id = (select auth.uid())));
create policy projects_insert on public.portfolio_projects for insert to authenticated
with check (student_id = (select auth.uid())
    and exists(select 1 from public.profiles where id = (select auth.uid()) and role = 'STUDENT'));
create policy projects_update on public.portfolio_projects for update to authenticated
using (student_id = (select auth.uid())) with check (student_id = (select auth.uid()));
create policy projects_delete on public.portfolio_projects for delete to authenticated
using (student_id = (select auth.uid()));
create trigger certiflow_project_recipient before insert or update of recipient_id on public.portfolio_projects
for each row execute function public.validate_staff_recipient();

alter table public.portfolios add column project_id uuid references public.portfolio_projects(id) on delete cascade;
create index portfolios_project_order on public.portfolios(project_id, added_at desc, id);
grant insert(project_id), update(title), delete on public.portfolios to authenticated;

create function public.validate_portfolio_project() returns trigger language plpgsql security definer
set search_path = '' as $$
begin
    if new.project_id is not null and not exists(select 1 from public.portfolio_projects
        where id = new.project_id and student_id = new.student_id) then
        raise exception 'Files must be attached to one of your own portfolio entries.' using errcode = '23514';
    end if;
    return new;
end;
$$;
revoke all on function public.validate_portfolio_project() from public, anon, authenticated;
create trigger certiflow_portfolio_project before insert or update of project_id, student_id on public.portfolios
for each row execute function public.validate_portfolio_project();

drop policy portfolios_read on public.portfolios;
create policy portfolios_read on public.portfolios for select to authenticated
using (student_id = (select auth.uid()) or public.is_admin()
    or (project_id is not null and exists(select 1 from public.portfolio_projects p
        where p.id = project_id and p.recipient_id = (select auth.uid()) and public.is_staff())));
create policy portfolios_update on public.portfolios for update to authenticated
using (student_id = (select auth.uid())) with check (student_id = (select auth.uid()));
create policy portfolios_delete on public.portfolios for delete to authenticated
using (student_id = (select auth.uid()));

-- SELECT follows document metadata, which follows the assigned project's RLS.
-- The metadata SELECT policy never queries storage, avoiding recursive RLS checks.
drop policy portfolio_files_read on storage.objects;
create policy portfolio_files_read on storage.objects for select to authenticated
using (bucket_id = 'portfolios' and (
    (storage.foldername(name))[1] = (select auth.uid())::text
    or exists(select 1 from public.portfolios p where p.stored_name = name)));

create table public.messages (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null default auth.uid() references public.profiles(id),
    recipient_id uuid not null references public.profiles(id),
    body text not null check (char_length(btrim(body)) between 1 and 4000),
    sent_at timestamptz not null default now(),
    read_at timestamptz,
    check (sender_id <> recipient_id),
    check (read_at is null or read_at >= sent_at)
);
create index messages_sender_time on public.messages(sender_id, sent_at desc, id);
create index messages_recipient_time on public.messages(recipient_id, sent_at desc, id);
create index messages_conversation on public.messages(sender_id, recipient_id, sent_at desc, id);
alter table public.messages enable row level security;
revoke all on public.messages from public, anon, authenticated;
grant select on public.messages to authenticated;
grant insert(recipient_id, body) on public.messages to authenticated;
create policy messages_read on public.messages for select to authenticated
using (sender_id = (select auth.uid()) or recipient_id = (select auth.uid()));
create policy messages_send on public.messages for insert to authenticated
with check (sender_id = (select auth.uid()));

create function public.validate_message() returns trigger language plpgsql security definer
set search_path = '' as $$
begin
    if new.sender_id is distinct from auth.uid() or new.sender_id = new.recipient_id
        or not exists(select 1 from public.profiles s join auth.users su on su.id = s.id
            cross join public.profiles r join auth.users ru on ru.id = r.id
            where s.id = new.sender_id and r.id = new.recipient_id
                and su.email_confirmed_at is not null and ru.email_confirmed_at is not null
                and (s.role in ('TEACHER','ADMIN') or r.role in ('TEACHER','ADMIN'))) then
        raise exception 'Choose a verified contact. Students can message staff; staff can message any verified account.' using errcode = '42501';
    end if;
    -- Serialize each sender's check so simultaneous requests cannot evade the limit.
    perform pg_advisory_xact_lock(hashtextextended(new.sender_id::text, 0));
    if (select count(*) from public.messages where sender_id = new.sender_id and sent_at > now() - interval '1 minute') >= 30 then
        raise exception 'Please wait a minute before sending more messages.' using errcode = '23514';
    end if;
    return new;
end;
$$;
revoke all on function public.validate_message() from public, anon, authenticated;
create trigger certiflow_validate_message before insert on public.messages
for each row execute function public.validate_message();

create function public.mark_messages_read(other_id uuid) returns integer
language plpgsql security definer set search_path = '' as $$
declare changed integer;
begin
    if auth.uid() is null then raise exception 'Sign in to read messages.' using errcode = '42501'; end if;
    update public.messages set read_at = now()
    where recipient_id = auth.uid() and sender_id = other_id and read_at is null;
    get diagnostics changed = row_count;
    return changed;
end;
$$;
revoke all on function public.mark_messages_read(uuid) from public, anon, authenticated;
grant execute on function public.mark_messages_read(uuid) to authenticated;

notify pgrst, 'reload schema';
commit;
