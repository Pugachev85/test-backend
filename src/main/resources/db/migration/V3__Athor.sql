create table author
(
    id          serial primary key,
    fio         text      not null,
    create_time timestamp not null
);