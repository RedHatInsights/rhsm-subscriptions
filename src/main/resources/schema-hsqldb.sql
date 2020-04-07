create table if not exists org_config(
  org_id varchar(255),
  sync_enabled boolean,
  opt_in_type varchar(255),
  created timestamp,
  updated timestamp
);
