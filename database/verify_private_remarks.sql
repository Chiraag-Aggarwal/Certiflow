-- Database-owner verification. All fixtures and publications roll back; no mail.
begin;
set local request.jwt.claim.sub='';
insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
('90000000-0000-4000-8000-000000000001','remark-student@example.invalid',now(),'{"full_name":"Remark student"}'),
('90000000-0000-4000-8000-000000000002','remark-other@example.invalid',now(),'{"full_name":"Other student"}'),
('90000000-0000-4000-8000-000000000003','remark-teacher@example.invalid',now(),'{"full_name":"Remark teacher"}'),
('90000000-0000-4000-8000-000000000004','remark-other-teacher@example.invalid',now(),'{"full_name":"Other teacher"}'),
('90000000-0000-4000-8000-000000000005','remark-admin@example.invalid',now(),'{"full_name":"Remark admin"}'),
('90000000-0000-4000-8000-000000000006','remark-viewer@example.invalid',now(),'{"full_name":"Remark viewer","account_type":"VIEWER"}');
update public.profiles set role='TEACHER' where id in ('90000000-0000-4000-8000-000000000003','90000000-0000-4000-8000-000000000004');
update public.profiles set role='ADMIN' where id='90000000-0000-4000-8000-000000000005';
-- Fixed IDs are seeded as the database owner: normal clients correctly lack
-- INSERT privileges on server-owned identity columns.
insert into public.requests(id,student_id,type,reason) values('90000000-0000-4000-8000-000000000011','90000000-0000-4000-8000-000000000001','Scholarship','Remark verification request');
insert into public.portfolio_projects(id,student_id,title,recipient_id) values
('90000000-0000-4000-8000-000000000012','90000000-0000-4000-8000-000000000001','Remark verification project','90000000-0000-4000-8000-000000000003');
insert into public.portfolio_projects(id,student_id,title,recipient_id) values
('90000000-0000-4000-8000-000000000013','90000000-0000-4000-8000-000000000001','Other teacher project','90000000-0000-4000-8000-000000000004');
set local role authenticated;

set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000003';
do $$ declare r public.private_remarks;begin
    r:=public.add_private_remark('NOC','90000000-0000-4000-8000-000000000011','DO_NOT_PUBLISH_PRIVATE_REMARK_NOC');
    if r.student_id<>'90000000-0000-4000-8000-000000000001' or r.author_id<>auth.uid() or r.author_name<>'Remark teacher' then raise exception 'Remark identities not derived from source and session';end if;
    perform public.add_private_remark('PROJECT','90000000-0000-4000-8000-000000000012','DO_NOT_PUBLISH_PRIVATE_REMARK_PROJECT');
    if (select count(*) from public.private_remarks where author_id=auth.uid())<>2 then raise exception 'Sender cannot see own remarks';end if;
    begin perform public.add_private_remark('PROJECT','90000000-0000-4000-8000-000000000013','Forbidden');raise exception 'Teacher wrote to an unassigned project';exception when insufficient_privilege then null;end;
    begin perform public.add_private_remark('NOC','90000000-0000-4000-8000-000000000099','Missing source');raise exception 'Missing source accepted';exception when insufficient_privilege then null;end;
    begin perform public.add_private_remark('NOC','90000000-0000-4000-8000-000000000011','  ');raise exception 'Blank remark accepted';exception when invalid_parameter_value then null;end;
    begin perform public.add_private_remark('NOC','90000000-0000-4000-8000-000000000011',repeat('x',2001));raise exception 'Oversized remark accepted';exception when invalid_parameter_value then null;end;
    begin perform public.add_private_remark('PROFILE','90000000-0000-4000-8000-000000000001','Wrong scope');raise exception 'Unsupported source accepted';exception when invalid_parameter_value then null;end;
end $$;

set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000004';
do $$ begin
    if not exists(select 1 from public.requests where id='90000000-0000-4000-8000-000000000011') then raise exception 'Shared queue fixture missing';end if;
    if exists(select 1 from public.private_remarks where student_id='90000000-0000-4000-8000-000000000001') then raise exception 'Other teacher read sender remarks';end if;
    perform public.add_private_remark('NOC','90000000-0000-4000-8000-000000000011','Other teacher private note');
    if (select count(*) from public.private_remarks where student_id='90000000-0000-4000-8000-000000000001')<>1 then raise exception 'Teacher sees remarks by another sender';end if;
end $$;

set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000005';
do $$ begin
    if (select count(*) from public.private_remarks where student_id='90000000-0000-4000-8000-000000000001')<>3 then raise exception 'Admin missing remarks';end if;
    perform public.add_private_remark('PROJECT','90000000-0000-4000-8000-000000000013','Admin guidance');
    if has_table_privilege('authenticated','public.private_remarks','INSERT,UPDATE,DELETE') or has_table_privilege('anon','public.private_remarks','SELECT') then raise exception 'Direct remark privileges too broad';end if;
    begin update public.private_remarks set student_id='90000000-0000-4000-8000-000000000002';raise exception 'Remark recipients could be reassigned';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000001';
do $$ declare payload jsonb;begin
    if (select count(*) from public.private_remarks where student_id=auth.uid())<>4 then raise exception 'Student missing private feedback';end if;
    begin perform public.add_private_remark('NOC','90000000-0000-4000-8000-000000000011','Student forgery');raise exception 'Student sent a staff remark';exception when insufficient_privilege then null;end;
    begin insert into public.private_remarks(request_id,student_id,author_id,author_name,body) values('90000000-0000-4000-8000-000000000011',auth.uid(),'90000000-0000-4000-8000-000000000005','Fake admin','Forged');raise exception 'Direct insert permitted';exception when insufficient_privilege then null;end;
    payload:=public.publish_share('NOC','90000000-0000-4000-8000-000000000011','NOC preview','Public summary','Student','','BOTH','{}');
    perform set_config('certiflow.remark_noc_token',payload->>'token',true);
    if payload::text like '%DO_NOT_PUBLISH%' then raise exception 'Published NOC leaked remarks';end if;
    payload:=public.publish_share('PROJECT','90000000-0000-4000-8000-000000000012','Project preview','Public summary','Student','','BOTH','{}');
    perform set_config('certiflow.remark_project_token',payload->>'token',true);
    if payload::text like '%DO_NOT_PUBLISH%' then raise exception 'Published project leaked remarks';end if;
end $$;

set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000002';
do $$ begin if exists(select 1 from public.private_remarks where student_id='90000000-0000-4000-8000-000000000001') then raise exception 'Another student read private remarks';end if;end $$;
set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000006';
do $$ begin
    if exists(select 1 from public.private_remarks) then raise exception 'Viewer read private remarks';end if;
    if exists(select 1 from public.community_shares() p where p::text like '%DO_NOT_PUBLISH%') then raise exception 'Community exposed private remarks';end if;
    begin perform public.add_private_remark('PROJECT','90000000-0000-4000-8000-000000000012','Viewer forgery');raise exception 'Viewer wrote remarks';exception when insufficient_privilege then null;end;
end $$;

reset role;
set local request.jwt.claim.sub='';
update public.profiles set role='VIEWER' where id in ('90000000-0000-4000-8000-000000000001','90000000-0000-4000-8000-000000000003');
set local role authenticated;
set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000001';
do $$ begin if exists(select 1 from public.private_remarks) then raise exception 'Former student viewer retained private remarks';end if;end $$;
set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000003';
do $$ begin if exists(select 1 from public.private_remarks) then raise exception 'Former teacher viewer retained private remarks';end if;end $$;
reset role;
set local request.jwt.claim.sub='';
update auth.users set email_confirmed_at=null where id='90000000-0000-4000-8000-000000000005';
set local role authenticated;
set local request.jwt.claim.sub='90000000-0000-4000-8000-000000000005';
do $$ begin
    if exists(select 1 from public.private_remarks) then raise exception 'Unverified admin read remarks';end if;
    begin perform public.add_private_remark('PROJECT','90000000-0000-4000-8000-000000000013','Unverified');raise exception 'Unverified admin wrote remark';exception when insufficient_privilege then null;end;
end $$;
reset role;
set local request.jwt.claim.sub='';
set local role anon;
do $$ declare payload jsonb;begin
    begin perform 1 from public.private_remarks;raise exception 'Anonymous remark read';exception when insufficient_privilege then null;end;
    begin perform public.add_private_remark('NOC','90000000-0000-4000-8000-000000000011','Anonymous');raise exception 'Anonymous remark write';exception when insufficient_privilege then null;end;
    payload:=public.get_public_share(current_setting('certiflow.remark_noc_token')::uuid);
    if payload is null or payload::text like '%DO_NOT_PUBLISH%' then raise exception 'Public NOC invalid or exposed remarks';end if;
    payload:=public.get_public_share(current_setting('certiflow.remark_project_token')::uuid);
    if payload is null or payload::text like '%DO_NOT_PUBLISH%' then raise exception 'Public project invalid or exposed remarks';end if;
end $$;
reset role;
rollback;
select 'Private remarks: recipient, sender and admin access passed; other students, teachers, viewers and public links excluded. All fixtures rolled back.' as result;
