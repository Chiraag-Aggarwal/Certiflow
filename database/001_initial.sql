begin;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null check (char_length(btrim(full_name)) between 1 and 120),
    email text not null,
    role text not null default 'STUDENT' check (role in ('STUDENT','TEACHER','ADMIN')),
    department text not null default '' check (char_length(department) <= 120),
    student_number text not null default '' check (char_length(student_number) <= 80),
    created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;

create function public.is_staff() returns boolean language sql stable security definer
set search_path = '' as $$
    select exists(select 1 from public.profiles where id = (select auth.uid()) and role in ('TEACHER','ADMIN'));
$$;
revoke all on function public.is_staff() from public, anon, authenticated;
grant execute on function public.is_staff() to authenticated;

create function public.create_profile() returns trigger language plpgsql security definer
set search_path = '' as $$
begin
    insert into public.profiles(id, full_name, email, department, student_number)
    values (new.id, coalesce(nullif(btrim(new.raw_user_meta_data->>'full_name'),''), new.email),
        new.email, coalesce(new.raw_user_meta_data->>'department',''),
        coalesce(new.raw_user_meta_data->>'student_number',''));
    return new;
end;
$$;
revoke all on function public.create_profile() from public, anon, authenticated;
create trigger certiflow_create_profile after insert on auth.users
for each row execute function public.create_profile();

create function public.sync_profile_email() returns trigger language plpgsql security definer
set search_path = '' as $$
begin
    update public.profiles set email = new.email where id = new.id;
    return new;
end;
$$;
revoke all on function public.sync_profile_email() from public, anon, authenticated;
create trigger certiflow_sync_email after update of email on auth.users
for each row execute function public.sync_profile_email();

create schema certiflow_private;
revoke all on schema certiflow_private from public, anon, authenticated;
create table certiflow_private.admin_bootstrap (
    email text primary key,
    claimed_by uuid unique references auth.users(id),
    claimed_at timestamptz
);
alter table certiflow_private.admin_bootstrap enable row level security;
revoke all on certiflow_private.admin_bootstrap from public, anon, authenticated;
create function public.claim_initial_admin() returns trigger language plpgsql security definer
set search_path = '' as $$
declare claimed uuid;
begin
    if new.email_confirmed_at is not null then
        update certiflow_private.admin_bootstrap set claimed_by = new.id, claimed_at = now()
        where lower(email) = lower(new.email) and claimed_by is null returning claimed_by into claimed;
        if claimed is not null then update public.profiles set role = 'ADMIN' where id = new.id; end if;
    end if;
    return new;
end;
$$;
revoke all on function public.claim_initial_admin() from public, anon, authenticated;
create trigger certiflow_promote_admin after insert or update of email_confirmed_at on auth.users
for each row execute function public.claim_initial_admin();

revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant update(full_name, department, student_number) on public.profiles to authenticated;
create policy profiles_read on public.profiles for select to authenticated
using (id = (select auth.uid()) or public.is_staff());
create policy profiles_update on public.profiles for update to authenticated
using (id = (select auth.uid())) with check (id = (select auth.uid()));

create table public.requests (
    id uuid primary key default gen_random_uuid(),
    student_id uuid not null default auth.uid() references public.profiles(id),
    student_name text not null,
    type text not null check (type in ('Library Clearance','Laboratory Access','Sports Equipment','Internship','Other')),
    reason text not null check (char_length(btrim(reason)) between 1 and 2000),
    status text not null default 'PENDING' check (status in ('PENDING','APPROVED','REJECTED')),
    submitted_at timestamptz not null default now(),
    decided_at timestamptz,
    decided_by uuid references public.profiles(id),
    decision_note text not null default '' check (char_length(decision_note) <= 2000),
    check ((status = 'PENDING' and decided_at is null and decided_by is null and decision_note = '')
        or (status <> 'PENDING' and decided_at is not null and decided_by is not null)),
    check (status <> 'REJECTED' or char_length(btrim(decision_note)) > 0)
);
create unique index requests_one_pending_type on public.requests(student_id,type) where status = 'PENDING';
create index requests_order on public.requests(submitted_at desc,id);
alter table public.requests enable row level security;

create function public.prepare_request() returns trigger language plpgsql security definer
set search_path = '' as $$
begin
    select full_name into new.student_name from public.profiles where id = new.student_id and role = 'STUDENT';
    if new.student_name is null then raise exception 'Only students can submit applications.'; end if;
    return new;
end;
$$;
revoke all on function public.prepare_request() from public, anon, authenticated;
create trigger certiflow_prepare_request before insert on public.requests
for each row execute function public.prepare_request();
revoke all on public.requests from anon, authenticated;
grant select on public.requests to authenticated;
grant insert(type,reason) on public.requests to authenticated;
create policy requests_read on public.requests for select to authenticated
using (student_id = (select auth.uid()) or public.is_staff());
create policy requests_submit on public.requests for insert to authenticated
with check (student_id = (select auth.uid()) and status = 'PENDING'
    and exists(select 1 from public.profiles where id = (select auth.uid()) and role = 'STUDENT'));

create function public.decide_request(request_id uuid, decision text, note text default '')
returns public.requests language plpgsql security definer set search_path = '' as $$
declare result public.requests;
begin
    if not public.is_staff() then raise exception 'Only staff can review applications.' using errcode = '42501'; end if;
    if decision is null or decision not in ('APPROVED','REJECTED') then raise exception 'Choose Approve or Reject.'; end if;
    note := btrim(coalesce(note,''));
    if char_length(note) > 2000 or (decision = 'REJECTED' and note = '') then raise exception 'Enter a rejection reason of up to 2,000 characters.'; end if;
    update public.requests set status = decision, decision_note = note, decided_at = now(), decided_by = auth.uid()
    where id = request_id and status = 'PENDING' returning * into result;
    if result.id is null then raise exception 'This application was already reviewed or no longer exists.'; end if;
    return result;
end;
$$;
revoke all on function public.decide_request(uuid,text,text) from public, anon, authenticated;
grant execute on function public.decide_request(uuid,text,text) to authenticated;

create table public.portfolios (
    id uuid primary key default gen_random_uuid(),
    student_id uuid not null default auth.uid() references public.profiles(id),
    title text not null check (char_length(btrim(title)) between 1 and 150),
    file_name text not null check (char_length(file_name) between 1 and 255),
    stored_name text not null unique,
    added_at timestamptz not null default now(),
    check (stored_name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.(pdf|png|jpg|jpeg|gif|webp|txt)$')
);
create index portfolios_owner_order on public.portfolios(student_id,added_at desc,id);
alter table public.portfolios enable row level security;
revoke all on public.portfolios from anon, authenticated;
grant select on public.portfolios to authenticated;
grant insert(title,file_name,stored_name) on public.portfolios to authenticated;
create policy portfolios_read on public.portfolios for select to authenticated
using (student_id = (select auth.uid()));
create policy portfolios_insert on public.portfolios for insert to authenticated
with check (student_id = (select auth.uid()) and split_part(stored_name,'/',1) = (select auth.uid())::text
    and exists(select 1 from public.profiles where id = (select auth.uid()) and role = 'STUDENT')
    and exists(select 1 from storage.objects where bucket_id = 'portfolios' and name = stored_name));

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('portfolios','portfolios',false,26214400,
    array['application/pdf','image/png','image/jpeg','image/gif','image/webp','text/plain']);
create policy portfolio_files_read on storage.objects for select to authenticated
using (bucket_id = 'portfolios' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy portfolio_files_insert on storage.objects for insert to authenticated
with check (bucket_id = 'portfolios' and (storage.foldername(name))[1] = (select auth.uid())::text
    and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.(pdf|png|jpg|jpeg|gif|webp|txt)$'
    and exists(select 1 from public.profiles where id = (select auth.uid()) and role = 'STUDENT'));
create policy portfolio_files_cleanup on storage.objects for delete to authenticated
using (bucket_id = 'portfolios' and (storage.foldername(name))[1] = (select auth.uid())::text
    and not exists(select 1 from public.portfolios where stored_name = name));

commit;
