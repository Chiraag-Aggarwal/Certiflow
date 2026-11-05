-- Run as the database owner after 003_collaboration.sql.
-- All isolated accounts, messages, and storage metadata are rolled back.
-- This script creates no physical storage objects and sends no emails.
begin;

insert into auth.users(id, email, email_confirmed_at, raw_user_meta_data) values
('30000000-0000-4000-8000-000000000001','collaboration-student-one@example.invalid',now(),'{"full_name":"Access check student one","role":"ADMIN","identity_verified":true}'),
('30000000-0000-4000-8000-000000000002','collaboration-student-two@example.invalid',now(),'{"full_name":"Access check student two"}'),
('30000000-0000-4000-8000-000000000003','collaboration-teacher-one@example.invalid',now(),'{"full_name":"Access check teacher one"}'),
('30000000-0000-4000-8000-000000000004','collaboration-teacher-two@example.invalid',now(),'{"full_name":"Access check teacher two"}'),
('30000000-0000-4000-8000-000000000005','collaboration-admin@example.invalid',now(),'{"full_name":"Access check administrator"}'),
('30000000-0000-4000-8000-000000000006','collaboration-unverified@example.invalid',null,'{"full_name":"Access check unverified account"}');
update public.profiles set role = 'TEACHER' where id in ('30000000-0000-4000-8000-000000000003','30000000-0000-4000-8000-000000000004','30000000-0000-4000-8000-000000000006');
update public.profiles set role = 'ADMIN' where id = '30000000-0000-4000-8000-000000000005';

-- Metadata rows model completed uploads for authorization checks only.
insert into storage.objects(bucket_id, name) values
('portfolios','30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000011.txt'),
('portfolios','30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000012.txt'),
('portfolios','30000000-0000-4000-8000-000000000002/30000000-0000-4000-8000-000000000013.txt');

do $$ begin
    if (select role from public.profiles where id = '30000000-0000-4000-8000-000000000001') <> 'STUDENT'
        or (select identity_verified from public.profiles where id = '30000000-0000-4000-8000-000000000001') then
        raise exception 'Signup metadata granted trusted privileges';
    end if;
    if has_function_privilege('anon','public.set_account_role(uuid,text)','EXECUTE')
        or has_function_privilege('anon','public.contact_directory()','EXECUTE')
        or has_function_privilege('anon','public.verify_identity(uuid)','EXECUTE')
        or has_function_privilege('anon','public.mark_messages_read(uuid)','EXECUTE') then
        raise exception 'Anonymous RPC access';
    end if;
    if has_table_privilege('authenticated','public.messages','UPDATE')
        or has_column_privilege('authenticated','public.messages','sender_id','INSERT')
        or has_column_privilege('authenticated','public.profiles','role','UPDATE')
        or has_column_privilege('authenticated','public.profiles','identity_verified','UPDATE') then
        raise exception 'Trusted columns are client-writable';
    end if;
end $$;

set local role authenticated;
set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000001';

do $$ declare new_id uuid; begin
    if exists(select 1 from public.contact_directory() where id in ('30000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000006')) then
        raise exception 'Directory exposed another student or an unverified account';
    end if;
    if not exists(select 1 from public.contact_directory() where id = '30000000-0000-4000-8000-000000000003') then
        raise exception 'Verified staff missing from directory';
    end if;
    begin update public.profiles set role = 'ADMIN' where id = auth.uid(); raise exception 'Role escalation succeeded'; exception when insufficient_privilege then null; end;
    begin update public.profiles set identity_verified = true where id = auth.uid(); raise exception 'Self-verification succeeded'; exception when insufficient_privilege then null; end;
    begin perform public.set_account_role(auth.uid(),'TEACHER'); raise exception 'Student assigned staff access'; exception when insufficient_privilege then null; end;
    begin perform public.verify_identity(auth.uid()); raise exception 'Student verified identity'; exception when insufficient_privilege then null; end;

    insert into public.requests(type,reason,recipient_id)
    values('Scholarship','Assigned authorization check','30000000-0000-4000-8000-000000000003') returning id into new_id;
    perform set_config('certiflow.check_request',new_id::text,true);
    insert into public.requests(type,reason) values('Library Clearance','Shared authorization check') returning id into new_id;
    perform set_config('certiflow.check_shared_request',new_id::text,true);
    begin insert into public.requests(type,reason,recipient_id) values('Other','Invalid recipient','30000000-0000-4000-8000-000000000002'); raise exception 'Student recipient accepted'; exception when check_violation then null; end;
    begin insert into public.requests(type,reason,recipient_id) values('Other','Unverified recipient','30000000-0000-4000-8000-000000000006'); raise exception 'Unverified recipient accepted'; exception when check_violation then null; end;

    insert into public.portfolio_projects(title,kind,description,link,recipient_id)
    values('Assigned project','Project','Authorization check','https://example.invalid/project','30000000-0000-4000-8000-000000000003') returning id into new_id;
    perform set_config('certiflow.check_project',new_id::text,true);
    insert into public.portfolios(title,file_name,stored_name,project_id)
    values('Assigned document','check.txt','30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000011.txt',new_id);
    insert into public.portfolio_projects(title,kind) values('Private project','Achievement') returning id into new_id;
    perform set_config('certiflow.check_private_project',new_id::text,true);
    insert into public.portfolios(title,file_name,stored_name,project_id)
    values('Private document','private.txt','30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000012.txt',new_id);
    begin insert into public.portfolio_projects(title,recipient_id) values('Invalid project recipient','30000000-0000-4000-8000-000000000002'); raise exception 'Project accepted a student recipient'; exception when check_violation then null; end;
    begin insert into public.portfolio_projects(title,link) values('Unsafe link','javascript:alert(1)'); raise exception 'Unsafe link accepted'; exception when check_violation then null; end;

    begin insert into public.messages(sender_id,recipient_id,body) values('30000000-0000-4000-8000-000000000003','30000000-0000-4000-8000-000000000004','Forged sender'); raise exception 'Forged sender accepted'; exception when insufficient_privilege then null; end;
    begin update public.messages set read_at = now(); raise exception 'Direct read receipt update permitted'; exception when insufficient_privilege then null; end;
    begin insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000002','Student to student'); raise exception 'Student-to-student chat accepted'; exception when insufficient_privilege then null; end;
    begin insert into public.messages(recipient_id,body) values(auth.uid(),'Self message'); raise exception 'Self message accepted'; exception when insufficient_privilege then null; end;
    begin insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000006','Unverified recipient'); raise exception 'Message to unverified recipient accepted'; exception when insufficient_privilege then null; end;
    insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000003','Private conversation check') returning id into new_id;
    perform set_config('certiflow.check_message',new_id::text,true);
    if public.mark_messages_read('30000000-0000-4000-8000-000000000003') <> 0 then raise exception 'Sender marked own outgoing message read'; end if;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000002';
do $$ begin
    if exists(select 1 from public.requests where student_id = '30000000-0000-4000-8000-000000000001')
        or exists(select 1 from public.portfolio_projects where student_id = '30000000-0000-4000-8000-000000000001')
        or exists(select 1 from public.portfolios where student_id = '30000000-0000-4000-8000-000000000001') then
        raise exception 'Another student can read submissions';
    end if;
    if exists(select 1 from storage.objects where bucket_id = 'portfolios' and name like '30000000-0000-4000-8000-000000000001/%') then raise exception 'Another student can download documents'; end if;
    begin insert into public.portfolios(title,file_name,stored_name,project_id)
        values('Foreign project','check.txt','30000000-0000-4000-8000-000000000002/30000000-0000-4000-8000-000000000013.txt',current_setting('certiflow.check_project')::uuid);
        raise exception 'Attachment added to another student project'; exception when check_violation then null; end;
    if exists(select 1 from public.messages where id = current_setting('certiflow.check_message')::uuid) then raise exception 'Another student can read chat'; end if;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000004';
do $$ begin
    if exists(select 1 from public.requests where id = current_setting('certiflow.check_request')::uuid) then raise exception 'Unassigned teacher sees private application'; end if;
    if not exists(select 1 from public.requests where student_id = '30000000-0000-4000-8000-000000000001' and recipient_id is null) then raise exception 'Shared staff queue unavailable'; end if;
    begin perform public.decide_request(current_setting('certiflow.check_request')::uuid,'APPROVED',''); raise exception 'Unassigned teacher reviewed application'; exception when insufficient_privilege then null; end;
    if exists(select 1 from public.portfolio_projects where student_id = '30000000-0000-4000-8000-000000000001')
        or exists(select 1 from public.portfolios where student_id = '30000000-0000-4000-8000-000000000001')
        or exists(select 1 from storage.objects where bucket_id = 'portfolios' and name like '30000000-0000-4000-8000-000000000001/%') then
        raise exception 'Unassigned teacher sees portfolios or files';
    end if;
    if exists(select 1 from public.messages where id = current_setting('certiflow.check_message')::uuid) then raise exception 'Unrelated teacher can read chat'; end if;
    begin perform public.set_account_role('30000000-0000-4000-8000-000000000002','TEACHER'); raise exception 'Teacher assigned privileges'; exception when insufficient_privilege then null; end;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000003';
do $$ begin
    if not exists(select 1 from public.requests where id = current_setting('certiflow.check_request')::uuid) then raise exception 'Assigned teacher cannot read application'; end if;
    perform public.decide_request(current_setting('certiflow.check_request')::uuid,'APPROVED','Authorization check');
    if (select status from public.requests where id = current_setting('certiflow.check_request')::uuid) <> 'APPROVED' then raise exception 'Assigned review did not persist'; end if;
    if not exists(select 1 from public.portfolio_projects where id = current_setting('certiflow.check_project')::uuid)
        or not exists(select 1 from public.portfolios where project_id = current_setting('certiflow.check_project')::uuid)
        or not exists(select 1 from storage.objects where bucket_id = 'portfolios' and name = '30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000011.txt') then
        raise exception 'Assigned teacher cannot read portfolio or file';
    end if;
    if exists(select 1 from public.portfolio_projects where id = current_setting('certiflow.check_private_project')::uuid)
        or exists(select 1 from storage.objects where bucket_id = 'portfolios' and name = '30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000012.txt') then
        raise exception 'Teacher sees unassigned private portfolio';
    end if;
    update public.portfolio_projects set title = 'Forbidden edit' where id = current_setting('certiflow.check_project')::uuid;
    if found then raise exception 'Teacher edited a student portfolio'; end if;
    if public.mark_messages_read('30000000-0000-4000-8000-000000000001') <> 1 then raise exception 'Recipient could not mark chat read'; end if;
    if (select read_at from public.messages where id = current_setting('certiflow.check_message')::uuid) is null then raise exception 'Read receipt missing'; end if;
    insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000001','Staff reply');
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000005';
do $$ begin
    if not exists(select 1 from public.portfolio_projects where id = current_setting('certiflow.check_private_project')::uuid) then raise exception 'Administrator review unavailable'; end if;
    if exists(select 1 from public.messages where id = current_setting('certiflow.check_message')::uuid) then raise exception 'Administrator can read unrelated private chat'; end if;
    begin perform public.set_account_role('30000000-0000-4000-8000-000000000002','ADMIN'); raise exception 'Client created another administrator'; exception when insufficient_privilege then null; end;
    begin perform public.set_account_role(auth.uid(),'STUDENT'); raise exception 'Administrator demoted self'; exception when insufficient_privilege then null; end;
    begin perform public.set_account_role('30000000-0000-4000-8000-000000000006','TEACHER'); raise exception 'Unverified account became teacher'; exception when insufficient_privilege then null; end;
    perform public.set_account_role('30000000-0000-4000-8000-000000000002','TEACHER');
    if (select role from public.profiles where id = '30000000-0000-4000-8000-000000000002') <> 'TEACHER' then raise exception 'Administrator role assignment failed'; end if;
    perform public.set_account_role('30000000-0000-4000-8000-000000000002','STUDENT');
    perform public.verify_identity('30000000-0000-4000-8000-000000000001');
    if not (select identity_verified from public.profiles where id = '30000000-0000-4000-8000-000000000001') then raise exception 'Identity verification did not persist'; end if;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000001';
do $$ begin
    update public.profiles set bio = 'Self-reported biography', skills = 'Writing', programme = 'Science', website = 'https://example.invalid/' where id = auth.uid();
    if not (select identity_verified from public.profiles where id = auth.uid()) then raise exception 'Biography edit incorrectly invalidated identity'; end if;
    update public.profiles set student_number = 'Changed institution ID' where id = auth.uid();
    if (select identity_verified from public.profiles where id = auth.uid())
        or (select verified_at from public.profiles where id = auth.uid()) is not null
        or (select verified_by from public.profiles where id = auth.uid()) is not null then
        raise exception 'Identity remained verified after identity fields changed';
    end if;
    if (select read_at from public.messages where id = current_setting('certiflow.check_message')::uuid) is null then raise exception 'Sender cannot see read receipt'; end if;
    if public.mark_messages_read('30000000-0000-4000-8000-000000000003') <> 1 then raise exception 'Student could not mark staff reply read'; end if;
    update public.portfolio_projects set title = 'Edited project', description = 'Updated description', recipient_id = '30000000-0000-4000-8000-000000000004'
    where id = current_setting('certiflow.check_project')::uuid;
    update public.portfolios set title = 'Renamed attachment' where project_id = current_setting('certiflow.check_project')::uuid;
end $$;

do $$ begin
    -- One earlier student-to-teacher message plus these 29 reaches the per-minute cap.
    for sequence_number in 1..29 loop
        insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000003','Rate-limit check ' || sequence_number);
    end loop;
    begin insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000003','Over the limit'); raise exception 'Message rate limit not enforced'; exception when check_violation then null; end;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000003';
do $$ begin
    if exists(select 1 from public.portfolio_projects where id = current_setting('certiflow.check_project')::uuid)
        or exists(select 1 from storage.objects where bucket_id = 'portfolios' and name = '30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000011.txt') then
        raise exception 'Old recipient retained portfolio access';
    end if;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000005';
do $$ begin perform public.set_account_role('30000000-0000-4000-8000-000000000004','STUDENT'); end $$;
set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000004';
do $$ begin
    if exists(select 1 from public.requests where id = current_setting('certiflow.check_shared_request')::uuid)
        or exists(select 1 from public.portfolio_projects where id = current_setting('certiflow.check_project')::uuid)
        or exists(select 1 from storage.objects where bucket_id = 'portfolios' and name = '30000000-0000-4000-8000-000000000001/30000000-0000-4000-8000-000000000011.txt') then
        raise exception 'Demoted teacher retained staff access';
    end if;
    begin perform public.decide_request(current_setting('certiflow.check_shared_request')::uuid,'APPROVED',''); raise exception 'Demoted teacher reviewed a request'; exception when insufficient_privilege then null; end;
    begin insert into public.messages(recipient_id,body) values('30000000-0000-4000-8000-000000000001','Demoted staff sender'); raise exception 'Demoted teacher messaged another student'; exception when insufficient_privilege then null; end;
end $$;

set local request.jwt.claim.sub = '30000000-0000-4000-8000-000000000001';
do $$ begin
    delete from public.portfolio_projects where id = current_setting('certiflow.check_project')::uuid;
    if exists(select 1 from public.portfolios where project_id = current_setting('certiflow.check_project')::uuid) then raise exception 'Project deletion left attachment metadata'; end if;
end $$;

reset role;
rollback;
select 'Collaboration access checks passed; all test rows rolled back.' as result;
