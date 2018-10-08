FROM centos:centos7

# workflow db

RUN mkdir -p /apps

COPY workflow_db/databases/workflow_db /apps/workflow_db

RUN mkdir -p /apps/workflow_db/config/

RUN ln -sf /apps/secrets/workflow_db/database.yml /apps/workflow_db/config/database.yml


# workflow ui

RUN mkdir -p /apps/workflow_ui/

COPY workflow_ui/target/workflow_ui.job.jar /apps/workflow_ui/

RUN mkdir -p /apps/workflow_ui/config/

RUN ln -sf /apps/secrets/workflow_ui/database.yml /apps/workflow_ui/config/database.yml && \
    ln -sf /apps/secrets/workflow_ui/environment.yml /apps/workflow_ui/config/environment.yml

# copy entrypoint to /apps/entrypoint.sh
COPY container/entrypoint.sh /apps/

