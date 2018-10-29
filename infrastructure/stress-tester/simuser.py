from multiprocessing import Process
from requests import ConnectionError
import random
import sys
import datetime
import time
import uuid
import json
from sducloud import *
from socket import gaierror

CREATE_DIR = 0
DELETE_DIR = 1
LIST_DIR = 2
UPLOAD = 3

#          time,usr,action,status,total_resptime,resptime,job_id
log_fmt = "{:07.3f},{:},{:},{:},{:07.3f},{:07.3f},{:},{:}"
fmt_str = "{:07.3f}  {:50}  {:10}  {:6}  {:07.3f}  {:32}"
fmt_str2 = fmt_str+"  {:}"

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
    error = None

    created_dirs = []
    deleted_dirs = 0
    interval = random.random()+1
    running = True

    while running:
        delta = time.time() - start_time
        req_type = 'none'
        do = random.randint(0, 3)

        if do == CREATE_DIR:
            req_type = "create_dir"
            dir_name = uuid.uuid4()

            try:
                resp = create_directory(dir_name, atoken)
            except gaierror:
                resp['status'] = 0
                error = "Possible DNS Error: Temporary failure in name resolution"
            except ConnectionError:
                resp['status'] = 0 
                error = "Connection Error"

            if resp['status'] == 200:
                created_dirs.append(dir_name)

        elif do == DELETE_DIR:
            req_type = "del_dir"
            if len(created_dirs) > 0:
                try:
                    resp = delete_directory(created_dirs[0], atoken)
                except gaierror:
                    resp['status'] = 0
                    error = "Possible DNS Error: Temporary failure in name resolution"
                except ConnectionError:
                    resp['status'] = 0 
                    error = "Connection Error"

                if resp['status'] == 200:
                    created_dirs.pop(0)
                    deleted_dirs += 1
                else:
                    resp['status'] = 0
        elif do == LIST_DIR:
            
            req_type = "list_dir"
            try:
                resp = list_directory(atoken)
            except gaierror:
                resp['status'] = 0
                error = "Possible DNS Error: Temporary failure in name resolution"
            except ConnectionError:
                resp['status'] = 0 
                error = "Connection Error"


        elif do == UPLOAD:
            req_type = "upload"
            try:
                resp = upload_random(atoken)
            except gaierror:
                resp['status'] = 0
                error = "Possible DNS Error: Temporary failure in name resolution"
            except ConnectionError:
                resp['status'] = 0 
                error = "Connection Error"


        if resp['status'] == 200:
            if ok_resp_num == 0:
                first_ok_time = time.time()
            ok_resp_num += 1
        if resp['status'] != 0:
            resp_num += 1

        # If an error occured (e.g. bad response), print accordingly, else print status
        if error is not None:
            print_str = fmt_str2.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['job_id'], error)
            log_str = log_fmt.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['ms'].total_seconds(), resp['job_id'], error)
            error = None
        else:
            print_str = fmt_str.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['job_id'])
            if req_type == "upload":
                log_str = log_fmt.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['ms'].total_seconds()/resp['size'], resp['job_id'], resp['size'])
            else:
                log_str = log_fmt.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['ms'].total_seconds(), resp['job_id'], "--")


        sim_log.append(log_str)
        print(print_str)
        time.sleep(interval)


        if time.time() - start_time >= timeout:
            running = False

    # Clean up
    for i in range(0, len(created_dirs)):
        req_type = "del_dir"
        try:
            resp = delete_directory(created_dirs[0], atoken)
        except gaierror:
            resp['status'] = 0
            print("Possible DNS Error: Temporary failure in name resolution")
        except ConnectionError:
            resp['status'] = 0 
            print("Connection Error")

        if resp['status'] == 200:
            created_dirs.pop(0)
            deleted_dirs += 1
        delta = time.time() - start_time
        print_str = fmt_str.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['job_id'])
        log_str = log_fmt.format(delta, user_name, req_type, resp['status'], resp['ms'].total_seconds(), resp['ms'].total_seconds(), resp['job_id'], "--")
        sim_log.append(log_str)
        print(print_str)

    
    delta = time.time() - start_time
    print_str = fmt_str2.format(delta, user_name, "done", 0, 0.0, "", "succ: " + str(ok_resp_num) + "/" + str(resp_num) + " first_ok: " + str(round(first_ok_time-start_time, 2)))
    log_str = log_fmt.format(delta, user_name, "done", 0, 0.0, 0.0, "", "succ:" + str(ok_resp_num) + "/" + str(resp_num) + " first_ok: " + str(round(first_ok_time-start_time, 2)))
    sim_log.append(log_str)
    print(print_str)

    if log_dir is not None:
        with open(os.path.join(log_dir, user_name + ".log"), 'w') as lf:
            for log in sim_log:
                lf.write(log + "\n")
                
    return 0

