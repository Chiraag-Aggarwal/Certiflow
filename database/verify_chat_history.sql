-- Run as database owner after 005_chat_history.sql.
-- Isolated records exist only inside this transaction. No email or files are sent.
begin;

insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values
('50000000-0000-4000-8000-000000000001','chat-history-student@example.invalid',now(),'{"full_name":"History check student"}'),
('50000000-0000-4000-8000-000000000002','chat-history-other-student@example.invalid',now(),'{"full_name":"History check other student"}'),
('50000000-0000-4000-8000-000000000003','chat-history-teacher@example.invalid',now(),'{"full_name":"History check teacher"}'),
('50000000-0000-4000-8000-000000000004','chat-history-other-teacher@example.invalid',now(),'{"full_name":"History check other teacher"}'),
('50000000-0000-4000-8000-000000000005','chat-history-admin@example.invalid',now(),'{"full_name":"History check administrator"}');
update public.profiles set role='TEACHER' where id in ('50000000-0000-4000-8000-000000000003','50000000-0000-4000-8000-000000000004');
update public.profiles set role='ADMIN' where id='50000000-0000-4000-8000-000000000005';

do $$ begin
    if has_function_privilege('anon','public.edit_message(uuid,text,text)','EXECUTE')
        or has_function_privilege('anon','public.chat_history(uuid,uuid,integer)','EXECUTE')
        or has_function_privilege('anon','public.chat_inbox()','EXECUTE') then raise exception 'Anonymous chat RPC access'; end if;
    if has_table_privilege('authenticated','public.messages','UPDATE')
        or has_column_privilege('authenticated','public.messages','edited_at','UPDATE') then raise exception 'Direct message editing permitted'; end if;
end $$;

-- Owner inserts use exact timestamps/IDs to exercise timestamp ties. The existing
-- trigger still validates each sender against the requested authenticated identity.
set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000001';
insert into public.messages(id,sender_id,recipient_id,body,sent_at) select
    ('50000000-0000-4000-8000-' || lpad(n::text,12,'0'))::uuid,
    '50000000-0000-4000-8000-000000000001'::uuid,
    '50000000-0000-4000-8000-000000000003'::uuid,
    'History message ' || n, now()-interval '2 days'
from generate_series(101,108) n;
insert into public.messages(id,sender_id,recipient_id,body,sent_at) values
('50000000-0000-4000-8000-000000000109','50000000-0000-4000-8000-000000000001','50000000-0000-4000-8000-000000000004','Separate conversation',now()-interval '1 day');

set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000003';
insert into public.messages(id,sender_id,recipient_id,body,sent_at) values
('50000000-0000-4000-8000-000000000110','50000000-0000-4000-8000-000000000003','50000000-0000-4000-8000-000000000001','Teacher reply',now()-interval '1 hour'),
('50000000-0000-4000-8000-000000000111','50000000-0000-4000-8000-000000000003','50000000-0000-4000-8000-000000000002','Unrelated private conversation',now());

set local role authenticated;
set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000001';

do $$ declare edited public.messages; saved_edit timestamptz; before_row public.messages;
    page_ids uuid[]; seen uuid[] := '{}'::uuid[]; cursor_id uuid := null; fetched integer;
begin
    if (select count(*) from public.chat_inbox()) <> 2 then raise exception 'Inbox failed to group conversations'; end if;
    if exists(select 1 from public.chat_inbox() where contact_id in ('50000000-0000-4000-8000-000000000002','50000000-0000-4000-8000-000000000005')) then raise exception 'Inbox exposed an unrelated account'; end if;
    if (select last_message from public.chat_inbox() where contact_id='50000000-0000-4000-8000-000000000003') <> 'Teacher reply'
        or (select unread_count from public.chat_inbox() where contact_id='50000000-0000-4000-8000-000000000003') <> 1 then raise exception 'Inbox latest message/unread count incorrect'; end if;
    if not (select can_send from public.chat_inbox() where contact_id='50000000-0000-4000-8000-000000000003') then raise exception 'Confirmed student-to-staff send unavailable'; end if;

    loop
        select array_agg(h.id order by h.sent_at desc,h.id desc) into page_ids
        from public.chat_history('50000000-0000-4000-8000-000000000003',cursor_id,2) h;
        fetched := coalesce(cardinality(page_ids),0);
        exit when fetched=0;
        if fetched>2 or seen && page_ids then raise exception 'History paging duplicated rows or ignored limit'; end if;
        seen := seen || page_ids;
        cursor_id := page_ids[fetched];
        if cardinality(seen)>9 then raise exception 'History returned a different conversation'; end if;
    end loop;
    if cardinality(seen)<>9 or seen[1]<>'50000000-0000-4000-8000-000000000110'::uuid
        or seen[2]<>'50000000-0000-4000-8000-000000000108'::uuid
        or seen[9]<>'50000000-0000-4000-8000-000000000101'::uuid then raise exception 'Keyset order lost older/tied messages'; end if;
    if (select count(*) from public.chat_history('50000000-0000-4000-8000-000000000003',null,0))<>1 then raise exception 'History minimum page size not enforced'; end if;
    begin perform public.chat_history('50000000-0000-4000-8000-000000000003','50000000-0000-4000-8000-000000000109',2); raise exception 'Cross-conversation anchor accepted'; exception when insufficient_privilege then null; end;
    begin perform public.chat_history('50000000-0000-4000-8000-000000000003','50000000-0000-4000-8000-000000000111',2); raise exception 'Another participant pair anchor accepted'; exception when insufficient_privilege then null; end;

    select * into before_row from public.messages where id='50000000-0000-4000-8000-000000000108';
    edited := public.edit_message(before_row.id,'  Corrected history message  ','History message 108');
    if edited.body<>'Corrected history message' or edited.edited_at is null or edited.sender_id<>before_row.sender_id
        or edited.recipient_id<>before_row.recipient_id or edited.sent_at<>before_row.sent_at
        or edited.read_at is distinct from before_row.read_at then raise exception 'Edit changed protected message fields'; end if;
    saved_edit := edited.edited_at;
    edited := public.edit_message(edited.id,'Corrected history message','Corrected history message');
    if edited.edited_at is distinct from saved_edit then raise exception 'No-op edit changed timestamp'; end if;
    begin perform public.edit_message(edited.id,'Overwrite stale version','History message 108'); raise exception 'Stale edit overwrote another change'; exception when serialization_failure then null; end;
    begin perform public.edit_message(edited.id,E' \t\n ','Corrected history message'); raise exception 'Blank message edit accepted'; exception when check_violation then null; end;
    begin perform public.edit_message(edited.id,repeat('x',4001),'Corrected history message'); raise exception 'Oversized message edit accepted'; exception when check_violation then null; end;
    begin perform public.edit_message('50000000-0000-4000-8000-000000000110','Forged teacher reply','Teacher reply'); raise exception 'Recipient edited another sender'; exception when insufficient_privilege then null; end;
    begin update public.messages set body='Direct overwrite' where id=edited.id; raise exception 'Direct text update permitted'; exception when insufficient_privilege then null; end;
    if public.mark_messages_read('50000000-0000-4000-8000-000000000003')<>1 then raise exception 'Read receipt update failed'; end if;
    if (select unread_count from public.chat_inbox() where contact_id='50000000-0000-4000-8000-000000000003')<>0 then raise exception 'Inbox unread count did not clear'; end if;
end $$;

set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000002';
do $$ begin
    if (select count(*) from public.chat_inbox())<>1
        or (select last_message from public.chat_inbox())<>'Unrelated private conversation' then raise exception 'Second student inbox is not isolated'; end if;
    if exists(select 1 from public.chat_history('50000000-0000-4000-8000-000000000001')) then raise exception 'Student read another pair history'; end if;
    begin perform public.edit_message('50000000-0000-4000-8000-000000000108','Forged edit','Corrected history message'); raise exception 'Outsider edited message'; exception when insufficient_privilege then null; end;
end $$;

set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000005';
do $$ begin
    if exists(select 1 from public.chat_inbox()) then raise exception 'Admin inbox exposes unrelated messages'; end if;
    if exists(select 1 from public.chat_history('50000000-0000-4000-8000-000000000001')) then raise exception 'Admin can read unrelated history'; end if;
    begin perform public.edit_message('50000000-0000-4000-8000-000000000108','Admin overwrite','Corrected history message'); raise exception 'Admin edited another sender'; exception when insufficient_privilege then null; end;
    perform public.set_account_role('50000000-0000-4000-8000-000000000003','STUDENT');
end $$;

set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000001';
do $$ begin
    if not exists(select 1 from public.chat_inbox() where contact_id='50000000-0000-4000-8000-000000000003' and not can_send and role='STUDENT') then raise exception 'Past conversation disappeared or retained send permission after demotion'; end if;
    if (select count(*) from public.chat_history('50000000-0000-4000-8000-000000000003'))<>9 then raise exception 'Old history unavailable after demotion'; end if;
    begin insert into public.messages(recipient_id,body) values('50000000-0000-4000-8000-000000000003','New student-to-student message'); raise exception 'Historical conversation bypassed send restrictions'; exception when insufficient_privilege then null; end;
    perform public.edit_message('50000000-0000-4000-8000-000000000108','Author correction after role change','Corrected history message');
end $$;

set local request.jwt.claim.sub='50000000-0000-4000-8000-000000000003';
do $$ declare result public.messages; begin
    if (select count(*) from public.chat_inbox())<>2 or exists(select 1 from public.chat_inbox() where can_send) then raise exception 'Demoted staff inbox/send state incorrect'; end if;
    if (select count(*) from public.chat_history('50000000-0000-4000-8000-000000000001'))<>9 then raise exception 'Demoted participant lost own history'; end if;
    result := public.edit_message('50000000-0000-4000-8000-000000000110','Corrected reply','Teacher reply');
    if result.read_at is null then raise exception 'Editing erased read receipt'; end if;
end $$;

reset role;
rollback;
select 'Chat history and editing checks passed; all test rows rolled back.' as result;
