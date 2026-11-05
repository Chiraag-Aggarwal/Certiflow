-- Execute as the database owner. Every fixture is rolled back, even on success.
begin;
insert into auth.users(id,email,raw_user_meta_data) values
('10000000-0000-4000-8000-000000000001','rls-student-one@example.invalid','{"full_name":"RLS check one","role":"ADMIN"}'),
('10000000-0000-4000-8000-000000000002','rls-student-two@example.invalid','{"full_name":"RLS check two"}'),
('10000000-0000-4000-8000-000000000003','rls-staff@example.invalid','{"full_name":"RLS check staff"}');
update public.profiles set role='TEACHER' where id='10000000-0000-4000-8000-000000000003';
do $$ begin
 if (select role from public.profiles where id='10000000-0000-4000-8000-000000000001') <> 'STUDENT' then raise exception 'Metadata granted admin role'; end if;
 if has_function_privilege('anon','public.decide_request(uuid,text,text)','EXECUTE') then raise exception 'Anonymous RPC access'; end if;
 if has_table_privilege('authenticated','certiflow_private.admin_bootstrap','SELECT') then raise exception 'Bootstrap table exposed'; end if;
end $$;
set local role authenticated;
set local request.jwt.claim.sub='10000000-0000-4000-8000-000000000001';
do $$ begin
 if (select count(*) from public.profiles) <> 1 then raise exception 'Student can read another profile'; end if;
 begin update public.profiles set role='ADMIN' where id=auth.uid(); raise exception 'Role escalation succeeded'; exception when insufficient_privilege then null; end;
 begin insert into public.requests(student_id,type,reason) values('10000000-0000-4000-8000-000000000002','Other','Check'); raise exception 'Forged student accepted'; exception when insufficient_privilege then null; end;
end $$;
insert into public.requests(type,reason) values('Other','Authorization check; transaction will be rolled back');
do $$ begin
 if (select count(*) from public.requests) <> 1 then raise exception 'Own request unavailable'; end if;
 begin insert into public.requests(type,reason) values('Other','Duplicate'); raise exception 'Duplicate accepted'; exception when unique_violation then null; end;
 begin perform public.decide_request((select id from public.requests limit 1),'APPROVED',''); raise exception 'Student approved request'; exception when insufficient_privilege then null; end;
 begin insert into public.portfolios(title,file_name,stored_name) values('Check','check.txt','10000000-0000-4000-8000-000000000001/10000000-0000-4000-8000-000000000004.txt'); raise exception 'Missing storage object accepted'; exception when insufficient_privilege then null; end;
end $$;
set local request.jwt.claim.sub='10000000-0000-4000-8000-000000000002';
do $$ begin if (select count(*) from public.requests) <> 0 then raise exception 'Cross-student request visible'; end if; end $$;
set local request.jwt.claim.sub='10000000-0000-4000-8000-000000000003';
do $$ begin
 if (select count(*) from public.requests where student_id='10000000-0000-4000-8000-000000000001') <> 1 then raise exception 'Staff cannot review'; end if;
 perform public.decide_request((select id from public.requests where student_id='10000000-0000-4000-8000-000000000001'),'APPROVED','Verified');
 if (select status from public.requests where student_id='10000000-0000-4000-8000-000000000001') <> 'APPROVED' then raise exception 'Decision not stored'; end if;
end $$;
rollback;
select 'Access checks passed; all test rows rolled back.' as result;
