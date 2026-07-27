alter table job_generated_task
    add column review_status varchar(255) not null default 'APPROVED';

alter table job_generated_task
    add column review_comment text;

alter table job_generated_task
    add column reviewed_at timestamp(6);
