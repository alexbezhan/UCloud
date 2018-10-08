from multiprocessing import Process
import random
import sys
import datetime
import time
import uuid
from sducloud import *

CREATE_DIR = 0
DELETE_DIR = 1
LIST_DIR = 2
UPLOAD = 3

fmt_str = "{:50} {:10} {:3} {:} {:32}"

def sim_user(timeout, access_token):
    # Create user
    user_name = "stresstester-" + str(uuid.uuid4())
    resp = create_user(user_name, access_token)

    if resp['status'] == 200:
        atoken = resp['access_token']
        rtoken = resp['refresh_token']
    else:
        print(fmt_str.format("--", "create_usr", resp['status'],  resp['ms'], resp['job_id']))
        sys.exit(0);

    time.sleep(2)

    created_dirs = []
    deleted_dirs = 0
    interval = 1
    start_time = time.time()
    running = True

    while running:
        req_type = 'none'
        do = random.randint(0, 3)

        if do == CREATE_DIR:
            req_type = "create_dir"
            dir_name = uuid.uuid4()
            resp = create_directory(dir_name, atoken)
            if resp['status'] == 200:
                created_dirs.append(dir_name)

        elif do == DELETE_DIR:
            req_type = "del_dir"
            if len(created_dirs) > 0:
                resp = delete_directory(created_dirs[0], atoken)
                if resp['status'] == 200:
                    created_dirs.pop(0)
                    deleted_dirs += 1

        elif do == LIST_DIR:
            req_type = "list_dir"
            resp = list_directory(atoken)

        elif do == UPLOAD:
            req_type = "upload"
            resp = upload_random(atoken)

        elapsed_str = "{}.{}".format(resp['ms'].seconds, resp['ms'].microseconds)
    
        print(fmt_str.format(user_name, req_type, resp['status'], elapsed_str, resp['job_id']))
        time.sleep(interval)

        if time.time() - start_time >= timeout:
            running = False

    # Clean up
    for i in range(0, len(created_dirs)):
        req_type = "del_dir"
        resp = delete_directory(created_dirs[0], atoken)
        if resp['status'] == 200:
            created_dirs.pop(0)
            deleted_dirs += 1
        print(fmt_str.format(user_name, req_type, resp['status'], elapsed_str, resp['job_id']))

    
    print("{:50} done".format(user_name))

    return 0
