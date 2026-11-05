-- Apply after 007_viewer_accounts.sql. No existing account is promoted, demoted,
-- or renamed. The initial administrator remains anchored to the claimed user ID.
begin;

create table certiflow_private.role_change_audit (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid,
    target_id uuid not null,
    previous_role text not null check (previous_role in ('STUDENT','VIEWER','TEACHER','ADMIN')),
    new_role text not null check (new_role in ('STUDENT','VIEWER','TEACHER','ADMIN')),
    changed_at timestamptz not null default clock_timestamp(),
    check (previous_role <> new_role)
);
-- Historical IDs deliberately have no cascading foreign keys. Deleting an
-- account must not erase its audit trail. NULL actor means a database/bootstrap
-- operation, never an invented user identity.
alter table certiflow_private.role_change_audit enable row level security;
revoke all on certiflow_private.role_change_audit from public, anon, authenticated;
create index role_change_audit_target_time on certiflow_private.role_change_audit(target_id, changed_at desc, id);

create function certiflow_private.reject_role_audit_mutation() returns trigger
language plpgsql set search_path = '' as $$
begin
    raise exception 'Role change history is immutable.' using errcode = '42501';
end;
$$;
revoke all on function certiflow_private.reject_role_audit_mutation() from public, anon, authenticated;
create trigger certiflow_role_audit_immutable before update or delete
on certiflow_private.role_change_audit for each row execute function certiflow_private.reject_role_audit_mutation();
create trigger certiflow_role_audit_no_truncate before truncate
on certiflow_private.role_change_audit for each statement execute function certiflow_private.reject_role_audit_mutation();

create function certiflow_private.record_role_change() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if new.role is distinct from old.role then
        insert into certiflow_private.role_change_audit(actor_id,target_id,previous_role,new_role)
        values (auth.uid(),new.id,old.role,new.role);
    end if;
    return new;
end;
$$;
revoke all on function certiflow_private.record_role_change() from public, anon, authenticated;
create trigger certiflow_record_role_change after update of role on public.profiles
for each row execute function certiflow_private.record_role_change();

-- The protected account cannot be demoted through any profile-update path.
-- The existing bootstrap foreign key also prevents deletion of its auth user.
create function certiflow_private.protect_founding_admin() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if new.role <> 'ADMIN' and exists(select 1 from certiflow_private.admin_bootstrap b where b.claimed_by=old.id) then
        raise exception 'The founding administrator account is protected.' using errcode='42501';
    end if;
    return new;
end;
$$;
revoke all on function certiflow_private.protect_founding_admin() from public, anon, authenticated;
create trigger certiflow_protect_founding_admin before update of role on public.profiles
for each row execute function certiflow_private.protect_founding_admin();

create function certiflow_private.can_manage_admins() returns boolean
language sql stable security definer set search_path = '' as $$
    select exists(select 1 from certiflow_private.admin_bootstrap b
        join public.profiles p on p.id=b.claimed_by
        join auth.users u on u.id=p.id
        where b.claimed_by=auth.uid() and p.role='ADMIN' and u.email_confirmed_at is not null);
$$;
revoke all on function certiflow_private.can_manage_admins() from public, anon, authenticated;

-- Return only the caller's capability, never the founding administrator's ID,
-- email, bootstrap row, or the private role-change history.
create function public.account_capabilities() returns jsonb
language sql stable security definer set search_path = '' as $$
    select jsonb_build_object('can_manage_admins',certiflow_private.can_manage_admins());
$$;
revoke all on function public.account_capabilities() from public, anon, authenticated;
grant execute on function public.account_capabilities() to authenticated;

create or replace function public.set_account_role(target_id uuid, new_role text)
returns public.profiles language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); result public.profiles;
begin
    if not public.is_admin() then raise exception 'Only administrators can assign account access.' using errcode='42501'; end if;
    if target_id is null or target_id=caller or new_role is null or new_role not in ('STUDENT','VIEWER','TEACHER','ADMIN') then
        raise exception 'Choose another verified account and a valid role.' using errcode='42501';
    end if;
    -- Lock both current roles in a stable order. An administrator demoted in a
    -- concurrent request cannot continue assigning access using a stale check.
    perform p.id from public.profiles p where p.id in (caller,target_id) order by p.id for update;
    if not public.is_admin() then raise exception 'Administrator access changed. Sign in again.' using errcode='42501'; end if;
    select p.* into result from public.profiles p where p.id=target_id;
    if result.id is null or not exists(select 1 from auth.users u where u.id=target_id and u.email_confirmed_at is not null) then
        raise exception 'Choose an existing account with a verified email.' using errcode='42501';
    end if;
    if exists(select 1 from certiflow_private.admin_bootstrap b where b.claimed_by=target_id) then
        raise exception 'The founding administrator account is protected.' using errcode='42501';
    end if;
    if (result.role='ADMIN' or new_role='ADMIN') and not certiflow_private.can_manage_admins() then
        raise exception 'Only the founding administrator can grant or remove administrator access.' using errcode='42501';
    end if;
    if result.role is distinct from new_role then
        update public.profiles p set role=new_role where p.id=target_id returning p.* into result;
    end if;
    return result;
end;
$$;
revoke all on function public.set_account_role(uuid,text) from public, anon, authenticated;
grant execute on function public.set_account_role(uuid,text) to authenticated;

notify pgrst, 'reload schema';
commit;
