-- Run as database owner after 006. No mail or physical uploads; all fixtures roll back.
begin;
insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
('60000000-0000-4000-8000-000000000001','sharing-owner@example.invalid',now(),'{"full_name":"Sharing check owner"}'),
('60000000-0000-4000-8000-000000000002','sharing-other@example.invalid',now(),'{"full_name":"Sharing check other"}');
insert into storage.objects(bucket_id,name) values
('portfolios','60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000011.txt'),
('portfolios','60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000012.txt');
do $$ begin
    if has_table_privilege('anon','public.share_publications','SELECT') or has_table_privilege('authenticated','public.share_publications','UPDATE')
        or has_function_privilege('anon','public.publish_share(text,uuid,text,text,text,text,text,uuid[])','EXECUTE')
        or has_function_privilege('anon','public.community_shares()','EXECUTE')
        or has_function_privilege('authenticated','public.share_payload(uuid)','EXECUTE') then raise exception 'Publication privileges are too broad';end if;
end $$;
set local role authenticated;
set local request.jwt.claim.sub='60000000-0000-4000-8000-000000000001';
do $$ declare p uuid;f uuid;r uuid;payload jsonb;begin
    insert into public.portfolio_projects(title,description) values('Private source title','DO_NOT_LEAK_PROJECT_DETAILS') returning id into p;
    perform set_config('certiflow.share_project',p::text,true);
    insert into public.portfolios(title,file_name,stored_name,project_id) values('Public evidence','evidence.txt','60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000011.txt',p) returning id into f;
    perform set_config('certiflow.share_file',f::text,true);
    insert into public.portfolios(title,file_name,stored_name,project_id) values('Unselected private file','private.txt','60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000012.txt',p);
    insert into public.requests(type,reason) values('Scholarship','DO_NOT_LEAK_NOC_REASON') returning id into r;
    perform set_config('certiflow.share_request',r::text,true);
    if public.get_own_share('PROJECT',p) is not null then raise exception 'Content published automatically';end if;
    payload:=public.publish_share('PROJECT',p,'Public work','Chosen public description','Public display name','https://example.invalid/work','BOTH',array[f]);
    perform set_config('certiflow.share_token',payload->>'token',true);
    if payload->>'title'<>'Public work' or jsonb_array_length(payload->'files')<>1 then raise exception 'Publication missing chosen content';end if;
    if payload::text like '%DO_NOT_LEAK%' or payload ? 'email' or payload ? 'student_number' or payload ? 'owner_id' then raise exception 'Private source data leaked';end if;
    if public.get_own_share('PROJECT',p)->>'token'<>payload->>'token' then raise exception 'Owner could not retrieve settings';end if;
    begin perform public.publish_share('NOC',r,'NOC','Summary','Owner','','BOTH',array[f]);raise exception 'NOC accepted unrelated project file';exception when insufficient_privilege then null;end;
    begin perform public.publish_share('PROJECT',p,'Work','','Owner','javascript:alert(1)','BOTH','{}');raise exception 'Unsafe public link accepted';exception when check_violation then null;end;
    begin perform public.publish_share('PROJECT',p,'Work','','Owner','https://user:password@example.invalid/work','BOTH','{}');raise exception 'Embedded URL credentials accepted';exception when check_violation then null;end;
    payload:=public.publish_share('NOC',r,'My scholarship application','A deliberately chosen summary','Public display name','','COMMUNITY','{}');
    perform set_config('certiflow.share_noc_token',payload->>'token',true);
    if payload->>'status'<>'PENDING' or payload->>'category'<>'Scholarship' or payload::text like '%DO_NOT_LEAK%' then raise exception 'NOC summary disclosed private details or forged status';end if;
end $$;

set local request.jwt.claim.sub='60000000-0000-4000-8000-000000000002';
do $$ declare result jsonb;begin
    begin perform public.publish_share('PROJECT',current_setting('certiflow.share_project')::uuid,'Stolen work','','Other','','LINK','{}');raise exception 'Another user published source';exception when insufficient_privilege then null;end;
    if public.get_own_share('PROJECT',current_setting('certiflow.share_project')::uuid) is not null then raise exception 'Another user read owner settings';end if;
    perform public.unpublish_share('PROJECT',current_setting('certiflow.share_project')::uuid);
    if public.get_public_share(current_setting('certiflow.share_token')::uuid) is null then raise exception 'Another user unpublished source';end if;
    if not exists(select 1 from public.community_shares() s where s->>'token'=current_setting('certiflow.share_noc_token')) then raise exception 'Community summary unavailable to signed-in account';end if;
    if exists(select 1 from public.portfolio_projects where id=current_setting('certiflow.share_project')::uuid)
        or exists(select 1 from public.requests where id=current_setting('certiflow.share_request')::uuid) then raise exception 'Community publication exposed private source rows';end if;
end $$;

reset role;
set local role anon;
set local request.jwt.claim.sub='';
do $$ declare payload jsonb;begin
    payload:=public.get_public_share(current_setting('certiflow.share_token')::uuid);
    if payload is null or payload->>'summary'<>'Chosen public description' then raise exception 'Public token unavailable';end if;
    if public.get_public_share(current_setting('certiflow.share_noc_token')::uuid) is not null then raise exception 'Community-only publication exposed anonymously';end if;
    if public.get_public_share('60000000-0000-4000-8000-000000000999') is not null then raise exception 'Unknown token returned data';end if;
    begin perform 1 from public.share_publications;raise exception 'Anonymous publication enumeration allowed';exception when insufficient_privilege then null;end;
    begin perform 1 from public.community_shares();raise exception 'Anonymous community enumeration allowed';exception when insufficient_privilege then null;end;
    if not public.can_read_published_file('60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000011.txt') then raise exception 'Selected file unavailable';end if;
    if public.can_read_published_file('60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000012.txt') then raise exception 'Unselected private file exposed';end if;
    perform set_config('storage.operation','',true);
    if exists(select 1 from storage.objects where bucket_id='portfolios' and name like '60000000-0000-4000-8000-000000000001/%') then raise exception 'Storage read without download operation exposed objects';end if;
    perform set_config('storage.operation','storage.object.list',true);
    if exists(select 1 from storage.objects where bucket_id='portfolios' and name like '60000000-0000-4000-8000-000000000001/%') then raise exception 'Anonymous storage listing exposed public files';end if;
    perform set_config('storage.operation','storage.object.get_authenticated',true);
    if (select count(*) from storage.objects where bucket_id='portfolios' and name like '60000000-0000-4000-8000-000000000001/%')<>1 then raise exception 'Anonymous storage scope incorrect';end if;
end $$;

reset role;
set local role authenticated;
set local request.jwt.claim.sub='60000000-0000-4000-8000-000000000001';
select public.publish_share('PROJECT',current_setting('certiflow.share_project')::uuid,'Public work','Chosen public description','Public display name','','COMMUNITY',array[current_setting('certiflow.share_file')::uuid]);
reset role;
set local role anon;
set local request.jwt.claim.sub='';
do $$ begin
    if public.get_public_share(current_setting('certiflow.share_token')::uuid) is not null
        or public.can_read_published_file('60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000011.txt') then raise exception 'Community-only change failed to revoke public access';end if;
end $$;
reset role;
set local role authenticated;
set local request.jwt.claim.sub='60000000-0000-4000-8000-000000000001';
do $$ declare payload jsonb;begin
    payload:=public.publish_share('PROJECT',current_setting('certiflow.share_project')::uuid,'Public work','Chosen public description','Public display name','','LINK',array[current_setting('certiflow.share_file')::uuid]);
    if payload->>'token'=current_setting('certiflow.share_token') then raise exception 'Community-to-public change reused a revoked token';end if;
    if public.get_public_share(current_setting('certiflow.share_token')::uuid) is not null then raise exception 'Old link revived after community-only interval';end if;
    perform set_config('certiflow.share_current_token',payload->>'token',true);
    perform public.unpublish_share('PROJECT',current_setting('certiflow.share_project')::uuid);
    if public.get_own_share('PROJECT',current_setting('certiflow.share_project')::uuid) is not null then raise exception 'Unpublish did not remove publication';end if;
    if public.get_public_share(current_setting('certiflow.share_current_token')::uuid) is not null then raise exception 'Unpublish left current token accessible';end if;
    payload:=public.publish_share('PROJECT',current_setting('certiflow.share_project')::uuid,'Published again','','Owner','','LINK',array[current_setting('certiflow.share_file')::uuid]);
    if payload->>'token'=current_setting('certiflow.share_token') or payload->>'token'=current_setting('certiflow.share_current_token') then raise exception 'Revoked public link reused';end if;
    perform set_config('certiflow.share_new_token',payload->>'token',true);
    if public.get_public_share(current_setting('certiflow.share_token')::uuid) is not null then raise exception 'Old link worked after republishing';end if;
    delete from public.portfolio_projects where id=current_setting('certiflow.share_project')::uuid;
    if public.get_public_share(current_setting('certiflow.share_new_token')::uuid) is not null
        or public.can_read_published_file('60000000-0000-4000-8000-000000000001/60000000-0000-4000-8000-000000000011.txt') then raise exception 'Source deletion failed to revoke publication';end if;
end $$;
reset role;
rollback;
select 'Public sharing privacy checks passed; all test rows rolled back.' as result;
