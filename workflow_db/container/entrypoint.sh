#!/usr/bin/env bash

# set up the database files from environment vars
sed -e "s/\${db_username}/$DB_USERNAME/" \
  -e "s/\${db_password}/$DB_PASSWORD/" \
  -e "s/\${db_address}/$DB_HOSTNAME/" \
  -e "s/\${db_address}/$DB_PORT/" \
  /apps/workflow_db/config/database.yml.tpl \
  > /apps/workflow_db/config/database.yml

function mysql_conn_error {
  mysql -h $DB_HOSTNAME -P $DB_PORT -u $DB_USERNAME --password=$DB_PASSWORD -e "show databases"
  return $?
}

# wait until mysql is running
while
    mysql_conn_error
do
    sleep 1
done

# set up the rails db
export RAILS_ENV=docker_env
cd /apps/workflow_db/
bundle exec rake db:create
bundle exec rake rapleaf:migrate
