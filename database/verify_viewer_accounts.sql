-- Run as database owner after 007. Isolated account/storage metadata fixtures
-- are rolled back; this sends no emails and creates no physical file objects.
begin;
select set_config('certiflow.viewer_bootstrap_before',
    coalesce((select md5(string_agg(row_to_json(b)::text, '' order by email)) from certiflow_private.admin_bootstrap b),''),true);

insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
('70000000-0000-4000-8000-000000000001','viewer-check-student@example.invalid',now(),'{"full_name":"Viewer check student","account_type":"STUDENT"}'),
('70000000-0000-4000-8000-000000000002','viewer-check-viewer@example.invalid',now(),'{"full_name":"Viewer check viewer","account_type":"VIEWER","role":"ADMIN","identity_verified":true}'),
('70000000-0000-4000-8000-000000000003','viewer-check-teacher@example.invalid',now(),'{"full_name":"Viewer check teacher"}'),
('70000000-0000-4000-8000-000000000004','viewer-check-admin@example.invalid',now(),'{"full_name":"Viewer check administrator"}'),
('70000000-0000-4000-8000-000000000005','viewer-check-unverified@example.invalid',null,'{"full_name":"Viewer check unverified","account_type":"VIEWER"}'),
('70000000-0000-4000-8000-000000000006','viewer-check-forged-teacher@example.invalid',now(),'{"full_name":"Forged teacher check","account_type":"TEACHER","role":"TEACHER"}'),
('70000000-0000-4000-8000-000000000007','viewer-check-forged-admin@example.invalid',now(),'{"full_name":"Forged administrator check","account_type":"ADMIN","role":"ADMIN"}'),
('70000000-0000-4000-8000-000000000008','viewer-check-other-viewer@example.invalid',now(),'{"full_name":"Viewer check second viewer","account_type":"VIEWER"}'),
('70000000-0000-4000-8000-000000000009','viewer-check-former-student@example.invalid',now(),'{"full_name":"Viewer check former student"}');
update public.profiles set role='TEACHER' where id='70000000-0000-4000-8000-000000000003';
update public.profiles set role='ADMIN' where id='70000000-0000-4000-8000-000000000004';
-- Updating user-controlled authentication metadata cannot change a stored role.
update auth.users set raw_user_meta_data='{"account_type":"TEACHER","role":"ADMIN"}'
where id='70000000-0000-4000-8000-000000000002';
insert into storage.objects(bucket_id,name) values
('portfolios','70000000-0000-4000-8000-000000000001/70000000-0000-4000-8000-000000000011.txt'),
('portfolios','70000000-0000-4000-8000-000000000001/70000000-0000-4000-8000-000000000012.txt'),
('portfolios','70000000-0000-4000-8000-000000000002/70000000-0000-4000-8000-000000000013.txt'),
('portfolios','70000000-0000-4000-8000-000000000009/70000000-0000-4000-8000-000000000014.txt');

do $$ begin
    if (select role from public.profiles where id='70000000-0000-4000-8000-000000000002')<>'VIEWER'
        or (select identity_verified from public.profiles where id='70000000-0000-4000-8000-000000000002')
        or exists(select 1 from public.profiles where id in ('70000000-0000-4000-8000-000000000006','70000000-0000-4000-8000-000000000007') and role<>'STUDENT') then
        raise exception 'Untrusted signup or later metadata granted trusted access';
    end if;
    if has_column_privilege('authenticated','public.profiles','role','UPDATE')
        or has_column_privilege('authenticated','public.profiles','identity_verified','UPDATE')
        or has_function_privilege('anon','public.set_account_role(uuid,text)','EXECUTE')
        or has_function_privilege('authenticated','public.create_profile()','EXECUTE') then
        raise exception 'Role or identity permissions are too broad';
    end if;
end $$;

set local role authenticated;
set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000001';
do $$ declare p uuid;f uuid;r uuid;m uuid;s jsonb;begin
    insert into public.portfolio_projects(title,description,recipient_id)
    values('Private original','PRIVATE_VIEWER_SOURCE','70000000-0000-4000-8000-000000000003') returning id into p;
    perform set_config('certiflow.viewer_project',p::text,true);
    insert into public.portfolios(title,file_name,stored_name,project_id)
    values('Selected evidence','public.txt','70000000-0000-4000-8000-000000000001/70000000-0000-4000-8000-000000000011.txt',p) returning id into f;
    insert into public.portfolios(title,file_name,stored_name,project_id)
    values('Private evidence','private.txt','70000000-0000-4000-8000-000000000001/70000000-0000-4000-8000-000000000012.txt',p);
    s:=public.publish_share('PROJECT',p,'Community work','Chosen summary','Chosen author','','COMMUNITY',array[f]);
    perform set_config('certiflow.viewer_share_token',s->>'token',true);
    insert into public.requests(type,reason,recipient_id)
    values('Scholarship','PRIVATE_VIEWER_REASON','70000000-0000-4000-8000-000000000003') returning id into r;
    perform set_config('certiflow.viewer_request',r::text,true);
    insert into public.messages(recipient_id,body) values('70000000-0000-4000-8000-000000000003','Private student chat') returning id into m;
    perform set_config('certiflow.viewer_private_message',m::text,true);
    begin insert into public.portfolio_projects(title,recipient_id) values('Invalid viewer recipient','70000000-0000-4000-8000-000000000002');
        raise exception 'Viewer accepted as staff recipient';exception when check_violation then null;end;
end $$;

set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000009';
do $$ declare p uuid;f uuid;s jsonb;begin
    insert into public.portfolio_projects(title) values('Earlier student project') returning id into p;
    perform set_config('certiflow.viewer_former_project',p::text,true);
    insert into public.portfolios(title,file_name,stored_name,project_id)
    values('Earlier file','earlier.txt','70000000-0000-4000-8000-000000000009/70000000-0000-4000-8000-000000000014.txt',p) returning id into f;
    perform set_config('certiflow.viewer_former_file',f::text,true);
    s:=public.publish_share('PROJECT',p,'Earlier public project','','Student','','LINK','{}');
    perform set_config('certiflow.viewer_former_token',s->>'token',true);
end $$;

set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000002';
do $$ declare m uuid;denied boolean:=false;s jsonb;begin
    if public.is_staff() or public.is_admin() then raise exception 'Viewer treated as staff';end if;
    if exists(select 1 from public.profiles where id<>auth.uid())
        or exists(select 1 from public.requests where id=current_setting('certiflow.viewer_request')::uuid)
        or exists(select 1 from public.portfolio_projects where id=current_setting('certiflow.viewer_project')::uuid)
        or exists(select 1 from public.portfolios where project_id=current_setting('certiflow.viewer_project')::uuid)
        or exists(select 1 from public.messages where id=current_setting('certiflow.viewer_private_message')::uuid) then
        raise exception 'Viewer gained private roster, submission or chat access';
    end if;
    update public.profiles set full_name='Updated viewer name',bio='My profile' where id=auth.uid();
    if not found then raise exception 'Viewer cannot edit own profile';end if;
    begin update public.profiles set role='TEACHER' where id=auth.uid();raise exception 'Viewer changed trusted role';exception when insufficient_privilege then null;end;
    begin update public.profiles set identity_verified=true where id=auth.uid();raise exception 'Viewer self-verified';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role(auth.uid(),'TEACHER');raise exception 'Viewer assigned staff access';exception when insufficient_privilege then null;end;
    begin perform public.decide_request(current_setting('certiflow.viewer_request')::uuid,'APPROVED','');raise exception 'Viewer reviewed NOC';exception when insufficient_privilege then null;end;
    begin insert into public.requests(type,reason) values('Library Clearance','Forbidden viewer NOC');
        exception when insufficient_privilege then denied:=true;
        when raise_exception then if sqlerrm='Only students can submit applications.' then denied:=true;else raise;end if;end;
    if not denied then raise exception 'Viewer submitted NOC';end if;
    begin insert into public.portfolio_projects(title) values('Forbidden viewer project');raise exception 'Viewer created project';exception when insufficient_privilege then null;end;
    begin insert into public.portfolios(title,file_name,stored_name) values('Forbidden file','file.txt','70000000-0000-4000-8000-000000000002/70000000-0000-4000-8000-000000000013.txt');
        raise exception 'Viewer created attachment';exception when insufficient_privilege then null;end;
    begin insert into storage.objects(bucket_id,name) values('portfolios','70000000-0000-4000-8000-000000000002/70000000-0000-4000-8000-000000000015.txt');
        raise exception 'Viewer uploaded file';exception when insufficient_privilege then null;end;
    begin perform public.publish_share('PROJECT',current_setting('certiflow.viewer_project')::uuid,'Stolen work','','Viewer','','LINK','{}');
        raise exception 'Viewer published source';exception when insufficient_privilege then null;end;
    select x into s from public.community_shares() x where x->>'token'=current_setting('certiflow.viewer_share_token');
    if s is null or s->>'summary'<>'Chosen summary' or s::text like '%PRIVATE_VIEWER%' then raise exception 'Viewer community snapshot missing or leaked private details';end if;
    if public.get_public_share(current_setting('certiflow.viewer_share_token')::uuid) is not null then raise exception 'Community-only item gained public link';end if;
    perform set_config('storage.operation','storage.object.get_authenticated',true);
    if not exists(select 1 from storage.objects where name='70000000-0000-4000-8000-000000000001/70000000-0000-4000-8000-000000000011.txt')
        or exists(select 1 from storage.objects where name='70000000-0000-4000-8000-000000000001/70000000-0000-4000-8000-000000000012.txt') then
        raise exception 'Viewer selected-file scope incorrect';
    end if;
    if exists(select 1 from public.contact_directory() where role not in ('TEACHER','ADMIN'))
        or not exists(select 1 from public.contact_directory() where id='70000000-0000-4000-8000-000000000003') then raise exception 'Viewer directory scope incorrect';end if;
    begin insert into public.messages(recipient_id,body) values('70000000-0000-4000-8000-000000000001','Forbidden student outreach');raise exception 'Viewer contacted student';exception when insufficient_privilege then null;end;
    begin insert into public.messages(recipient_id,body) values('70000000-0000-4000-8000-000000000008','Forbidden viewer outreach');raise exception 'Viewer contacted viewer';exception when insufficient_privilege then null;end;
    insert into public.messages(recipient_id,body) values('70000000-0000-4000-8000-000000000003','Viewer question') returning id into m;
    perform set_config('certiflow.viewer_message',m::text,true);
    perform public.edit_message(m,'Edited viewer question','Viewer question');
    if not exists(select 1 from public.chat_history('70000000-0000-4000-8000-000000000003') where id=m and body='Edited viewer question')
        or not exists(select 1 from public.chat_inbox() where contact_id='70000000-0000-4000-8000-000000000003' and can_send) then raise exception 'Viewer staff chat unavailable';end if;
end $$;

set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000005';
do $$ begin
    begin insert into public.messages(recipient_id,body) values('70000000-0000-4000-8000-000000000003','Unverified viewer message');raise exception 'Unverified viewer sent message';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000003';
do $$ begin
    if not public.is_staff() or public.is_admin() then raise exception 'Teacher role behavior changed';end if;
    if not exists(select 1 from public.requests where id=current_setting('certiflow.viewer_request')::uuid)
        or not exists(select 1 from public.portfolio_projects where id=current_setting('certiflow.viewer_project')::uuid) then raise exception 'Existing teacher access changed';end if;
    perform public.decide_request(current_setting('certiflow.viewer_request')::uuid,'APPROVED','');
    insert into public.messages(recipient_id,body) values('70000000-0000-4000-8000-000000000002','Teacher reply to viewer');
    begin perform public.set_account_role('70000000-0000-4000-8000-000000000002','TEACHER');raise exception 'Teacher assigned roles';exception when insufficient_privilege then null;end;
end $$;

set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000004';
do $$ begin
    if not public.is_admin() or not public.is_staff() then raise exception 'Administrator role behavior changed';end if;
    begin perform public.set_account_role('70000000-0000-4000-8000-000000000002','ADMIN');raise exception 'Admin promotion exposed';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role(auth.uid(),'VIEWER');raise exception 'Administrator self-demotion allowed';exception when insufficient_privilege then null;end;
    begin perform public.set_account_role('70000000-0000-4000-8000-000000000005','TEACHER');raise exception 'Unverified account granted staff access';exception when insufficient_privilege then null;end;
    perform public.set_account_role('70000000-0000-4000-8000-000000000008','STUDENT');
    if (select role from public.profiles where id='70000000-0000-4000-8000-000000000008')<>'STUDENT' then raise exception 'Admin student assignment failed';end if;
    perform public.set_account_role('70000000-0000-4000-8000-000000000008','TEACHER');
    if (select role from public.profiles where id='70000000-0000-4000-8000-000000000008')<>'TEACHER' then raise exception 'Admin teacher assignment failed';end if;
    perform public.set_account_role('70000000-0000-4000-8000-000000000008','VIEWER');
    if (select role from public.profiles where id='70000000-0000-4000-8000-000000000008')<>'VIEWER' then raise exception 'Admin viewer assignment failed';end if;
    perform public.set_account_role('70000000-0000-4000-8000-000000000009','VIEWER');
end $$;

set local request.jwt.claim.sub='70000000-0000-4000-8000-000000000009';
do $$ begin
    update public.portfolio_projects set title='Forbidden former owner edit' where id=current_setting('certiflow.viewer_former_project')::uuid;
    if found then raise exception 'Viewer edited earlier project';end if;
    update public.portfolios set title='Forbidden former owner edit' where id=current_setting('certiflow.viewer_former_file')::uuid;
    if found then raise exception 'Viewer edited earlier attachment';end if;
    delete from public.portfolios where id=current_setting('certiflow.viewer_former_file')::uuid;
    if found then raise exception 'Viewer deleted earlier attachment';end if;
    delete from public.portfolio_projects where id=current_setting('certiflow.viewer_former_project')::uuid;
    if found then raise exception 'Viewer deleted earlier project';end if;
    begin perform public.publish_share('PROJECT',current_setting('certiflow.viewer_former_project')::uuid,'Forbidden viewer revision','','Viewer','','LINK','{}');
        raise exception 'Viewer published an earlier owned project';exception when insufficient_privilege then null;end;
    perform public.unpublish_share('PROJECT',current_setting('certiflow.viewer_former_project')::uuid);
    if public.get_public_share(current_setting('certiflow.viewer_former_token')::uuid) is not null then raise exception 'Viewer could not revoke own previous public link';end if;
end $$;

reset role;
do $$ begin
    if coalesce((select md5(string_agg(row_to_json(b)::text, '' order by email)) from certiflow_private.admin_bootstrap b),'')
        <> current_setting('certiflow.viewer_bootstrap_before') then raise exception 'Initial administrator bootstrap changed';end if;
end $$;
rollback;
select 'Viewer account and permission checks passed; all test rows rolled back.' as result;
