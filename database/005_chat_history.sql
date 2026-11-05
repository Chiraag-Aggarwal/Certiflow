-- Apply once after 004_storage_policy.sql. Existing messages are preserved.
begin;

alter table public.messages add column edited_at timestamptz,
    add constraint messages_edit_after_send check (edited_at is null or edited_at >= sent_at);

-- Message authors can change only their own text. A stale editor cannot silently
-- overwrite an edit made from another window; recipients and administrators have
-- no extra editing authority. Direct table UPDATE remains unavailable.
create function public.edit_message(message_id uuid, new_body text, original_body text)
returns public.messages language plpgsql security definer set search_path = '' as $$
declare result public.messages; clean_body text;
begin
    if auth.uid() is null then raise exception 'Sign in to edit a message.' using errcode = '42501'; end if;
    clean_body := regexp_replace(coalesce(new_body, ''), '^[[:space:]]+|[[:space:]]+$', '', 'g');
    if char_length(clean_body) not between 1 and 4000 then
        raise exception 'Write a message between 1 and 4,000 characters.' using errcode = '23514';
    end if;
    select m.* into result from public.messages m
    where m.id = message_id and m.sender_id = auth.uid() for update;
    if result.id is null then raise exception 'You can edit only your own messages.' using errcode = '42501'; end if;
    if result.body is distinct from original_body then
        raise exception 'This message changed elsewhere. Refresh the conversation before editing again.' using errcode = '40001';
    end if;
    if result.body is distinct from clean_body then
        update public.messages m set body = clean_body, edited_at = clock_timestamp()
        where m.id = result.id returning m.* into result;
    end if;
    return result;
end;
$$;
revoke all on function public.edit_message(uuid,text,text) from public, anon, authenticated;
grant execute on function public.edit_message(uuid,text,text) to authenticated;

-- Stable keyset paging does not skip/duplicate records when new messages arrive.
-- Anchors are resolved on the server to preserve full timestamp precision.
create function public.chat_history(other_id uuid, before_id uuid default null, page_size integer default 50)
returns setof public.messages language plpgsql stable security definer set search_path = '' as $$
declare caller uuid := auth.uid(); anchor_time timestamptz; anchor_id uuid;
begin
    if caller is null or other_id is null or other_id = caller then
        raise exception 'Choose another account to view a conversation.' using errcode = '42501';
    end if;
    if before_id is not null then
        select m.sent_at, m.id into anchor_time, anchor_id from public.messages m
        where m.id = before_id and ((m.sender_id = caller and m.recipient_id = other_id)
            or (m.sender_id = other_id and m.recipient_id = caller));
        if anchor_id is null then raise exception 'This history position does not belong to your conversation.' using errcode = '42501'; end if;
    end if;
    return query select m.* from public.messages m
    where ((m.sender_id = caller and m.recipient_id = other_id)
        or (m.sender_id = other_id and m.recipient_id = caller))
        and (before_id is null or (m.sent_at, m.id) < (anchor_time, anchor_id))
    order by m.sent_at desc, m.id desc
    limit greatest(1, least(coalesce(page_size, 50), 100));
end;
$$;
revoke all on function public.chat_history(uuid,uuid,integer) from public, anon, authenticated;
grant execute on function public.chat_history(uuid,uuid,integer) to authenticated;

-- The inbox exposes only people who already share a conversation with the caller.
-- Historic chats remain readable if staff roles change; can_send reflects current
-- verified roles and does not grant permission independently of validate_message.
create function public.chat_inbox()
returns table(contact_id uuid, full_name text, role text, department text,
    last_message text, last_sent_at timestamptz, unread_count bigint, can_send boolean)
language sql stable security definer set search_path = '' as $$
    with own_messages as (
        select m.*, case when m.sender_id = auth.uid() then m.recipient_id else m.sender_id end as other_id
        from public.messages m
        where auth.uid() is not null and (m.sender_id = auth.uid() or m.recipient_id = auth.uid())
    ), latest as (
        select distinct on (m.other_id) m.other_id, m.body, m.sent_at, m.id
        from own_messages m order by m.other_id, m.sent_at desc, m.id desc
    ), unread as (
        select m.other_id, count(*) as total from own_messages m
        where m.recipient_id = auth.uid() and m.read_at is null group by m.other_id
    )
    select p.id, p.full_name, p.role, p.department, latest.body, latest.sent_at,
        coalesce(unread.total, 0::bigint),
        (caller_auth.email_confirmed_at is not null and other_auth.email_confirmed_at is not null
            and (caller.role in ('TEACHER','ADMIN') or p.role in ('TEACHER','ADMIN')))
    from latest
    join public.profiles p on p.id = latest.other_id
    join auth.users other_auth on other_auth.id = p.id
    join public.profiles caller on caller.id = auth.uid()
    join auth.users caller_auth on caller_auth.id = caller.id
    left join unread on unread.other_id = p.id
    order by latest.sent_at desc, latest.id desc;
$$;
revoke all on function public.chat_inbox() from public, anon, authenticated;
grant execute on function public.chat_inbox() to authenticated;

notify pgrst, 'reload schema';
commit;
