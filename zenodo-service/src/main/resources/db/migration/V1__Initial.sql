set search_path to zenodo;

create sequence hibernate_sequence
  start 1
  increment 1;

create table publication_data_objects (
  data_object_path varchar(1024) not null,
  created_at       timestamp     not null,
  modified_at      timestamp     not null,
  uploaded         boolean       not null,
  publication_id   int8          not null,
  primary key (data_object_path, publication_id)
);

create table publications (
  id          int8         not null,
  created_at  timestamp    not null,
  modified_at timestamp    not null,
  name        varchar(255) not null,
  owner       varchar(255) not null,
  status      varchar(255) not null,
  zenodo_id   varchar(255),
  primary key (id)
);

alter table if exists publication_data_objects
  add constraint FKcn1dokv1quhqluax9v5us3fb1
foreign key (publication_id)
references publications;

create table zen_oauth_state_tokens (
  owner       varchar(255)  not null,
  created_at  timestamp     not null,
  modified_at timestamp     not null,
  return_to   varchar(1024) not null,
  token       varchar(255)  not null,
  primary key (owner)
);

create table zen_oauth_tokens (
  owner         varchar(255) not null,
  access_token  varchar(255) not null,
  created_at    timestamp    not null,
  expires_at    timestamp    not null,
  modified_at   timestamp    not null,
  refresh_token varchar(255),
  primary key (owner)
);