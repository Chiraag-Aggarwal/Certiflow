-- Apply after 003_collaboration.sql. No accounts, documents, or objects are changed.
begin;

-- A portfolios INSERT checks whether its uploaded storage object exists. Keeping
-- a direct portfolios subquery in the storage SELECT policy creates a policy
-- expansion cycle. This owner-executed function checks the exact same visibility
-- using underlying rows without recursively expanding their RLS policies.
create function public.can_read_portfolio_file(object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
    select auth.uid() is not null and exists(
        select 1 from public.portfolios f
        left join public.portfolio_projects p on p.id = f.project_id
        where f.stored_name = object_name
            and (f.student_id = auth.uid() or public.is_admin()
                or (public.is_staff() and p.recipient_id = auth.uid()))
    );
$$;
revoke all on function public.can_read_portfolio_file(text) from public, anon, authenticated;
grant execute on function public.can_read_portfolio_file(text) to authenticated;

drop policy portfolio_files_read on storage.objects;
create policy portfolio_files_read on storage.objects for select to authenticated
using (bucket_id = 'portfolios' and (
    (storage.foldername(name))[1] = (select auth.uid())::text
    or public.can_read_portfolio_file(name)));

notify pgrst, 'reload schema';
commit;
