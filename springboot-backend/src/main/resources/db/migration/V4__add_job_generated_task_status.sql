alter table job_generated_task
    add column status varchar(255) not null default 'SUCCESS';

alter table job_generated_task
    add column error_message text;
