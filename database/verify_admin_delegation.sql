-- Run as database owner after 008. Every account, role change and audit entry
-- below is an isolated fixture and rolls back. No real user is promoted.
begin;
set local request.jwt.claim.sub='';
do $$ begin
    perform set_config('certiflow.delegation_bootstrap_before',coalesce((select
        md5(string_agg(row_to_json(b)::text, '' order by email)) from certiflow_private.admin_bootstrap b),''),true);
end $$;

insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
('80000000-0000-4000-8000-000000000001','delegation-founder@example.invalid',now(),'{"full_name":"Delegation fixture founder"}'),
('80000000-0000-4000-8000-000000000002','delegation-admin@example.invalid',now(),'{"full_name":"Delegation fixture admin"}'),
('80000000-0000-4000-8000-000000000003','delegation-target@example.invalid',now(),'{"full_name":"Delegation fixture target"}'),
('80000000-0000-4000-8000-000000000004','delegation-viewer@example.invalid',now(),'{"full_name":"Delegation fixture viewer","account_type":"VIEWER"}'),
('80000000-0000-4000-8000-000000000005','delegation-teacher@example.invalid',now(),'{"full_name":"Delegation fixture teacher"}'),
('80000000-0000-4000-8000-000000000006','delegation-unverified@example.invalid',null,'{"full_name":"Delegation fixture unverified"}'),
('80000000-0000-4000-8000-000000000007','delegation-forged@example.invalid',now(),'{"full_name":"Delegation fixture forged","account_type":"ADMIN","role":"ADMIN","can_manage_admins":true}'),
('80000000-0000-4000-8000-000000000008','delegation-peer-admin@example.invalid',now(),'{"full_name":"Delegation fixture peer admin"}'),
('80000000-0000-4000-8000-000000000009','delegation-unverified-admin@example.invalid',null,'{"full_name":"Delegation fixture unverified admin"}');
update public.profiles set role='ADMIN' where id in ('80000000-0000-4000-8000-000000000001','80000000-0000-4000-8000-000000000002','80000000-0000-4000-8000-000000000008','80000000-0000-4000-8000-000000000009');
update public.profiles set role='TEACHER' where id='80000000-0000-4000-8000-000000000005';
insert into certiflow_private.admin_bootstrap(email,claimed_by,claimed_at)
values('delegation-founder@example.invalid','80000000-0000-4000-8000-000000000001',now());

do $$ begin
    if (select role from public.profiles where id='80000000-0000-4000-8000-000000000007')<>'STUDENT'
        or has_function_privilege('anon','public.account_capabilities()','EXECUTE')
        or has_function_privilege('anon','public.set_account_role(uuid,text)','EXECUTE')
        or has_function_privilege('authenticated','certiflow_private.can_manage_admins()','EXECUTE')
        or has_function_privilege('authenticated','certiflow_private.record_role_change()','EXECUTE')
        or has_table_privilege('authenticated','certiflow_private.role_change_audit','SELECT,INSERT,UPDATE,DELETE,TRUNCATE')
        or has_table_privilege('anon','certiflow_private.role_change_audit','SELECT,INSERT,UPDATE,DELETE,TRUNCATE')
        or has_column_privilege('authenticated','public.profiles','role','UPDATE') then
        raise exception 'Administrator delegation privileges are too broad';
    end if;
end $$;

set local role authenticated;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000001';
do $$ declare caps jsonb; changed public.profiles; begin
    caps:=public.account_capabilities();
    if caps<>jsonb_build_object('can_manage_admins',true) then raise exception 'Founder capability missing or leaked extra fields';end if;
    changed:=public.set_account_role('80000000-0000-4000-8000-000000000003','ADMIN');
    if changed.role<>'ADMIN' then raise exception 'Founder administrator grant failed';end if;
    -- Repeating the chosen role is idempotent and creates no false audit event.
    perform public.set_account_role('80000000-0000-4000-8000-000000000003','ADMIN');
    begin perform public.set_account_role(auth.uid(),'VIEWER');raise exception 'Founder could demote self';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000006','ADMIN');raise exception 'Founder promoted unverified target';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000009','TEACHER');raise exception 'Founder changed unverified administrator';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000004','OWNER');raise exception 'Invalid role accepted';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000099','ADMIN');raise exception 'Nonexistent account accepted';exception when insufficient_privilege then null;end;
    begin perform 1 from certiflow_private.role_change_audit;raise exception 'Audit history exposed to app administrator';exception when insufficient_privilege then null;end;
    begin insert into certiflow_private.role_change_audit(actor_id,target_id,previous_role,new_role)
        values(auth.uid(),'80000000-0000-4000-8000-000000000004','VIEWER','ADMIN');raise exception 'App administrator forged audit entry';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000003';
do $$ begin
    if not public.is_admin() or public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then
        raise exception 'Delegated administrator inherited founder authority or lacks normal admin access';end if;
    perform public.set_account_role('80000000-0000-4000-8000-000000000004','TEACHER');
    perform public.set_account_role('80000000-0000-4000-8000-000000000004','VIEWER');
    perform public.set_account_role('80000000-0000-4000-8000-000000000005','STUDENT');
    perform public.set_account_role('80000000-0000-4000-8000-000000000005','TEACHER');
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000004','ADMIN');raise exception 'Delegated admin granted administrator role';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000002','VIEWER');raise exception 'Delegated admin demoted peer administrator';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000001','TEACHER');raise exception 'Delegated admin changed founding owner';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role(auth.uid(),'STUDENT');raise exception 'Delegated admin changed self';exception when insufficient_privilege then null;end;
    begin update public.profiles set role='ADMIN' where id='80000000-0000-4000-8000-000000000004';raise exception 'Direct administrator promotion permitted';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000002';
do $$ begin
    if public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Existing normal admin gained founder authority';end if;
    perform public.set_account_role('80000000-0000-4000-8000-000000000007','VIEWER');
    perform public.set_account_role('80000000-0000-4000-8000-000000000007','STUDENT');
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000008','TEACHER');raise exception 'Normal admin demoted peer administrator';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000001','VIEWER');raise exception 'Normal admin changed founding owner';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000007';
do $$ begin
    if public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Student metadata granted founder capability';end if;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000004','ADMIN');raise exception 'Student granted administrator access';exception when insufficient_privilege then null;end;
end $$;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000004';
do $$ begin
    if public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Viewer gained founder capability';end if;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000007','TEACHER');raise exception 'Viewer assigned roles';exception when insufficient_privilege then null;end;
end $$;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000005';
do $$ begin
    if public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Teacher gained founder capability';end if;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000007','TEACHER');raise exception 'Teacher assigned roles';exception when insufficient_privilege then null;end;
end $$;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000009';
do $$ begin
    if public.is_admin() or public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Unverified administrator received trusted capabilities';end if;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000007','TEACHER');raise exception 'Unverified administrator assigned roles';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000001';
do $$ declare changed public.profiles;begin
    changed:=public.set_account_role('80000000-0000-4000-8000-000000000003','VIEWER');
    if changed.role<>'VIEWER' then raise exception 'Founder could not revoke delegated administrator access';end if;
    if not public.is_admin() or public.account_capabilities()<>jsonb_build_object('can_manage_admins',true) then raise exception 'Founder access changed while delegating';end if;
end $$;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000003';
do $$ begin
    if public.is_admin() or public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Demoted administrator retained trusted access';end if;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000004','TEACHER');raise exception 'Demoted administrator still assigned roles';exception when insufficient_privilege then null;end;
end $$;

-- Founder authority follows the claimed account ID, never a mutable email label.
reset role;
set local request.jwt.claim.sub='';
update auth.users set email='delegation-founder-renamed@example.invalid' where id='80000000-0000-4000-8000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000001';
do $$ begin
    if public.account_capabilities()<>jsonb_build_object('can_manage_admins',true) then raise exception 'Founder authority depends on mutable email text';end if;
end $$;
reset role;
set local request.jwt.claim.sub='';
update auth.users set email_confirmed_at=null where id='80000000-0000-4000-8000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub='80000000-0000-4000-8000-000000000001';
do $$ begin
    if public.account_capabilities()<>jsonb_build_object('can_manage_admins',false) then raise exception 'Unverified founder retained delegation capability';end if;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000004','ADMIN');raise exception 'Unverified founder delegated administrator access';exception when insufficient_privilege then null;end;
end $$;
reset role;
set local request.jwt.claim.sub='';
update auth.users set email_confirmed_at=now() where id='80000000-0000-4000-8000-000000000001';

do $$ begin
    if (select count(*) from certiflow_private.role_change_audit where target_id='80000000-0000-4000-8000-000000000003')<>2
        or not exists(select 1 from certiflow_private.role_change_audit where actor_id='80000000-0000-4000-8000-000000000001'
            and target_id='80000000-0000-4000-8000-000000000003' and previous_role='STUDENT' and new_role='ADMIN' and changed_at is not null)
        or not exists(select 1 from certiflow_private.role_change_audit where actor_id='80000000-0000-4000-8000-000000000001'
            and target_id='80000000-0000-4000-8000-000000000003' and previous_role='ADMIN' and new_role='VIEWER')
        or (select count(*) from certiflow_private.role_change_audit where actor_id='80000000-0000-4000-8000-000000000003'
            and target_id in ('80000000-0000-4000-8000-000000000004','80000000-0000-4000-8000-000000000005'))<>4 then
        raise exception 'Role audit omitted changes or included failed/no-op requests';
    end if;
    begin update certiflow_private.role_change_audit set new_role='STUDENT' where target_id='80000000-0000-4000-8000-000000000003';
        raise exception 'Audit entry was editable';exception when insufficient_privilege then null;end;
    begin delete from certiflow_private.role_change_audit where target_id='80000000-0000-4000-8000-000000000003';
        raise exception 'Audit entry was deletable';exception when insufficient_privilege then null;end;
    begin truncate table certiflow_private.role_change_audit;raise exception 'Audit history could be truncated';exception when insufficient_privilege then null;end;
    begin update public.profiles set role='STUDENT' where id='80000000-0000-4000-8000-000000000001';
        raise exception 'Direct profile update orphaned founder authority';exception when insufficient_privilege then null;end;
    if coalesce((select md5(string_agg(row_to_json(b)::text, '' order by email)) from certiflow_private.admin_bootstrap b
        where email<>'delegation-founder@example.invalid'),'')<>current_setting('certiflow.delegation_bootstrap_before') then
        raise exception 'Existing initial administrator bootstrap changed';
    end if;
end $$;

set local role anon;
set local request.jwt.claim.sub='';
do $$ begin
    begin perform public.account_capabilities();raise exception 'Anonymous account capability access';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('80000000-0000-4000-8000-000000000004','ADMIN');raise exception 'Anonymous role assignment';exception when insufficient_privilege then null;end;
end $$;
reset role;
rollback;
select 'Administrator delegation and audit checks passed; all test rows rolled back.' as result;
