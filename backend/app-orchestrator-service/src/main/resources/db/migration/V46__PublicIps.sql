create sequence application_sequence start 1 increment 1;

create table address_applications
(
  id                bigint       not null,
  created_at        timestamp    not null,
  approved_at       timestamp    default null,
  released_at       timestamp    default null,
  ip                text         default null,
  status            varchar(255) not null,
  applicant_id      text         not null,
  applicant_type    text         not null,
  application       text         not null,
  primary key (id)
);

create table ip_pool (
  ip          text not null,
  owner_id    text,
  owner_type  text,
  primary key (ip)
);

create table open_ports (
  application_id       bigint not null,
  port                 integer not null check (port >= 0 and port <= 65535),
  protocol             varchar(3),
  primary key (application_id, port, protocol)
);

create or replace function allocate_or_release_ip() returns trigger as $$
begin
    if new.status = 'APPROVED' then
        update ip_pool set owner_id = new.applicant_id, owner_type = new.applicant_type where ip = new.ip;
    elsif new.status = 'RELEASED' then
        update ip_pool set owner_id = null, owner_type = null where ip = new.ip;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace function remove_open_ports() returns trigger as $$
begin
    if new.status = 'RELEASED' then
        delete from open_ports where application_id = new.id;
    end if;
    return null;
end;
$$ language plpgsql;

create trigger trigger_approve_or_reject_application
    after update on address_applications
    for each row
    execute procedure allocate_or_release_ip();

create trigger trigger_remove_open_ports_on_release
    after update on address_applications
    for each row
    execute procedure remove_open_ports();