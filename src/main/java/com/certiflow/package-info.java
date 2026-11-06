/**
 * Certiflow's desktop client: JavaFX views, application data and Supabase access.
 *
 * <p>Start with {@link com.certiflow.App} for startup and role-based navigation.
 * Views use {@link com.certiflow.Ui} for the shared page shell and responsive
 * controls, {@link com.certiflow.PageComponents} for section headings and tags,
 * and {@link com.certiflow.Background} for blocking I/O. Data operations
 * live in {@link com.certiflow.AppData}; {@link com.certiflow.SupabaseApi} owns HTTP
 * transport and the in-memory authentication session.</p>
 *
 * <p>Client validation provides useful feedback, while database grants, row-level
 * security and checked RPCs enforce authority. Public sharing uses separate,
 * owner-reviewed previews rather than serializing private application records.
 * See {@code documentation/DEVELOPMENT.md} for the class map, common change paths
 * and the separate Java and database verification workflows.</p>
 */
package com.certiflow;
