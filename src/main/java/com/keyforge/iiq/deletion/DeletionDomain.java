package com.keyforge.iiq.deletion;

/**
 * A CSS domain eligible for deletion detection: its normalized table and PK column. Reusable — new
 * domains are added to {@link DeletionDomains} and given a source-id supplier at the call site.
 *
 * @param name     logical domain name (report/ledger key)
 * @param table    normalized PostgreSQL table (unqualified; schema applied at run time)
 * @param pkColumn primary-key column compared against the authoritative source id set
 */
public record DeletionDomain(String name, String table, String pkColumn) {
}
