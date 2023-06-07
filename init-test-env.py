import os
import argparse
import shutil
import subprocess
import sys
import time

testing_env_dir = os.path.abspath('./.testing_env')
if not os.path.exists(testing_env_dir):
  os.makedirs(testing_env_dir)
current_time = time.time()
stdout_log_file = open(os.path.join(testing_env_dir, 'logs_stdout_{}'.format(current_time)), 'w')
docker_repo_dir = os.path.join(testing_env_dir, 'plante_docker')

def step(msg):
  print('#### {}'.format(msg), end='\r\n')

def check_call(cmd):
  stdout_log_file.write('### Executing cmd: {}\n'.format(cmd))
  subprocess.check_call(cmd, shell=True, stdout=stdout_log_file)

def call(cmd):
  stdout_log_file.write('### Executing cmd: {}\n'.format(cmd))
  subprocess.call(cmd, shell=True, stdout=stdout_log_file)

def run(cmd):
  stdout_log_file.write('### Executing cmd: {}\n'.format(cmd))
  return subprocess.run(cmd, shell=True, stdout=stdout_log_file)

def Popen(cmd):
  stdout_log_file.write('### Executing cmd: {}\n'.format(cmd))
  return subprocess.Popen(cmd, shell=True, stdout=stdout_log_file)

def main(args):
  parser = argparse.ArgumentParser()
  parser.add_argument('--osm-testing-user', required=True, help='User at master.apis.dev.openstreetmap.org')
  parser.add_argument('--osm-testing-password', required=True, help='Password of user at master.apis.dev.openstreetmap.org')
  parser.add_argument('--off-testing-user', required=True, help='User at openfoodfacts.net')
  parser.add_argument('--off-testing-password', required=True, help='Password of user at openfoodfacts.net')
  parser.add_argument('--ios-server-private-key-path', default='/not/existing/path', help='Optional path to the backend iOS private key')
  parser.add_argument('--aws-s3-bucket-name', default='plante.files.testing', help='S3 bucket which will be used for testing')
  parser.add_argument('--aws-s3-region', default='eu-central-1', help='S3 bucket\'s region')
  parser.add_argument('--aws-s3-access-key-id', required=True,
                      help='Access key ID of your AWS user, which has the rights to write to the provided S3 bucket')
  parser.add_argument('--aws-s3-secret-access-key', required=True,
                      help='Secret access key of your AWS user, which has the rights to write to the provided S3 bucket')
  parser.add_argument('--psql-execute-file', required=False,
                      help='In most cases the file would be a backup file, execution of which would put its data into the started DB')
  args = parser.parse_args()
  step('Args: {}'.format(args))
  step('std out log file: {}'.format(stdout_log_file))

  step('Ensuring docker repo existence')
  if os.path.exists(docker_repo_dir) and not os.path.isdir(docker_repo_dir):
    raise RuntimeError('Folder with docker repo is unexpectedly not folder {}'.format(docker_repo_dir))
  elif not os.path.exists(docker_repo_dir):
    step('No docker repo dir found - clonning docker repo into dir: {}'.format(docker_repo_dir))
    os.makedirs(docker_repo_dir, exist_ok=True)
    check_call('git clone https://github.com/plante-app-team/plante_docker {}'.format(docker_repo_dir))
  else:
    step('Docker repo dir found, no need to clone the repo')

  step('Pulling last commits from docker repo')
  pull_cmd='git -C {} pull'.format(docker_repo_dir)
  check_call(pull_cmd)

  step('Building db container')
  db_container_dir = os.path.join(docker_repo_dir, 'db')
  users_password = '123'
  psql_user = 'main_user'
  psql_db = 'main'
  db_container_name = 'db_container_for_plante_server_tests'
  check_call('docker build -t {} {} --build-arg USER_PASSWORD={}'.format(db_container_name, db_container_dir, users_password))

  step('Trying to stop already spinning db container')
  grep_db_process = run('docker ps -a | grep {}'.format(db_container_name))
  if grep_db_process.returncode != 1:
    step('Spinning db container found, stopping and removing it')
    check_call('docker stop {0} && docker rm {0}'.format(db_container_name))
  else:
    step('Spinning db not found')

  step('Starting db container in background')
  docker_run_process = Popen('docker run --name={} -p 5432:5432 -it {}'.format(db_container_name, db_container_name))

  sleep_time = 7
  step('Sleeping for {} seconds to wait for db container start'.format(sleep_time))
  time.sleep(sleep_time)

  if args.psql_execute_file:
    step('Executing psql_execute_file: {}'.format(args.psql_execute_file))
    if not os.path.exists(args.psql_execute_file) or os.path.isdir(args.psql_execute_file):
      raise RuntimeError('psql_execute_file does not exist or is a folder: {}'.format(args.psql_execute_file))
    check_call('docker cp {} db_container_for_plante_server_tests:/extra.sql'.format(args.psql_execute_file))
    check_call('docker exec -it db_container_for_plante_server_tests psql -U {} -d {} -f /extra.sql'
      .format(psql_user, psql_db))

  step('Generating testing config')
  config_template = '''
  {{
   "psql_url": "{}",
   "psql_user": "{}",
   "psql_pass": "{}",
   "db_connection_attempts_timeout_seconds": 10,
   "jwt_secret": "not so secret secret",
   "ios_backend_private_key_file_path": "{}",
   "osm_testing_user": "{}",
   "osm_testing_password": "{}",
   "off_testing_user": "{}",
   "off_testing_password": "{}",
   "osm_prod_user": "intentionally invalid so that tests wouldn't mess prod osm db",
   "osm_prod_password": "",
   "off_prod_user": "intentionally invalid so that tests wouldn't mess prod off db",
   "off_prod_password": "",
   "aws_s3_access_key_id": "{}",
   "aws_s3_secret_access_key": "{}",
   "aws_s3_region": "{}",
   "aws_s3_bucket_name": "{}",
   "always_moderator_name": "local always moderator",
   "allow_cors": true,
   "metrics_endpoint": "doesnt_matter_its_not_tested"
  }}
  '''
  postgres_url = 'postgresql://localhost/{}'.format(psql_db)
  config = config_template.format(
    postgres_url,
    psql_user,
    users_password,
    args.ios_server_private_key_path,
    args.osm_testing_user,
    args.osm_testing_password,
    args.off_testing_user,
    args.off_testing_password,
    args.aws_s3_access_key_id,
    args.aws_s3_secret_access_key,
    args.aws_s3_region,
    args.aws_s3_bucket_name)

  step('Writing config to a file')
  config_file = os.path.join(testing_env_dir, 'testing_config.json')
  with open(config_file, 'w') as opened_config_file:
    opened_config_file.write(config)
  step('Wrote config to file: {}'.format(config_file))

  step('Blocking until db container finishes')
  step('')
  step('You can start the tests now, don\'t forget to export env var PLANTE_BACKEND_CONFIG_FILE_PATH with value: {}'.format(config_file))
  step('You can connect to the running psql now by executing: docker exec -it {} psql -U {} -d {}'.format(
    db_container_name, psql_user, psql_db))

  docker_run_process.communicate()
  step('Finishing')

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
