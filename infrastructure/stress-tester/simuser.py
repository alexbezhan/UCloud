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

fmt_str = "{:06.3f} {:50} {:10} {:3} {:} {:32}"

def sim_user(user_name, timeout, atoken, log_dir):
    # Create user
    start_time = time.time()
    first_ok_time = 0
    ok_resp_num = 0
    resp_num = 0
    sim_log = []
    resp = {}
    resp['status'] = 0
    resp['ms'] = datetime.timedelta()
    resp['job_id'] = ''

    created_dirs = []
    deleted_dirs = 0
    interval = random.random()+1
    running = True
    ignore = False

    while running:
        delta = time.time() - start_time
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
            else:
                ignore = True


        elif do == LIST_DIR:
            req_type = "list_dir"
            resp = list_directory(atoken)

        elif do == UPLOAD:
            req_type = "upload"
            resp = upload_random(atoken)

        if resp['status'] == 200:
            if ok_resp_num == 0:
                first_ok_time = time.time()
            ok_resp_num += 1
        resp_num += 1

        if not ignore: 
            elapsed_str = "{}.{}".format(resp['ms'].seconds, resp['ms'].microseconds)
            log_str = fmt_str.format(delta, user_name, req_type, resp['status'], elapsed_str, resp['job_id'])
            sim_log.append(log_str)
            print(log_str)
            time.sleep(interval)
            ignore = False

        if time.time() - start_time >= timeout:
            running = False

    # Clean up
    for i in range(0, len(created_dirs)):
        req_type = "del_dir"
        resp = delete_directory(created_dirs[0], atoken)
        if resp['status'] == 200:
            created_dirs.pop(0)
            deleted_dirs += 1
        delta = time.time() - start_time
        log_str = fmt_str.format(delta, user_name, req_type, resp['status'], elapsed_str, resp['job_id'])
        sim_log.append(log_str)
        print(log_str)

    
    delta = time.time() - start_time
    log_str = "{:06.3f} {:50} {:10} {:3} {:}/{:}, first OK: {:}".format(delta, user_name, "done", "OK:", ok_resp_num, resp_num, first_ok_time-start_time)
    sim_log.append(log_str)
    print(log_str)

    if log_dir is not None:
        with open(os.path.join(log_dir, user_name + ".log"), 'w') as lf:
            for log in sim_log:
                lf.write(log + "\n")
                
    return 0