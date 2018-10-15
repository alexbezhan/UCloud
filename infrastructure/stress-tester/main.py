import requests
import jwt
import sys
import time
import pathlib
import re
import base64
from simuser import sim_user
import os
from sducloud import *
import random
from multiprocessing import Pool, TimeoutError, Barrier, Process
import json
import uuid

########## PROGRAM PARAMETERS ##########

# Max duration in seconds. Will terminate all processes, even if they are not done.
timeout = 20

# Max duration each simulated user can spend (seconds)
sim_duration = 10

# Spawn interval (seconds) between simulated users
spawn_interval = 1

# Number of users to simulate
sim_num = 1

# Directory to write log files (optional)
log_dir = None

# Users file (users will be reused from this file, if it exists (optional)
sim_file = None

# Parse arguments
for arg_val in sys.argv:
    arg = ""
    val = ""
    if re.match(".*=.*", arg_val):
        arg_val = arg_val.split('=')
        arg = arg_val[0]
        val = arg_val[1]

    if re.match("timeout", arg):
        timeout = int(val)
    if re.match("sim_duration", arg):
        sim_duration = int(val)
    if re.match("spawn_interval", arg):
        spawn_interval = int(val)
    if re.match("sim_num", arg):
        sim_num = int(val)
    if re.match("log_dir", arg):
        log_dir = val
    if re.match("sim_file", arg):
        sim_file = val



# Print arguments
print("""Using arguments:
        timeout: {}
        sim_duration: {}
        spawn_interval: {}
        sim_num: {}
        log_dir: {}
    """.format(timeout, sim_duration, spawn_interval, sim_num, log_dir)
    )

# Will change this later
refresh_token = "1156c434-1a82-4ef8-a8ed-a16c85ae4d4d"

########## END OF PARAMETERS ##########

# Create log directory if it does not already exist
if log_dir is not None:
    if not os.path.exists(log_dir):
        pathlib.Path(log_dir).mkdir(exist_ok=True)

access_token = ''

# Fetch access token
renew_resp = renew_access_token(access_token, refresh_token)
if renew_resp['status'] == 200:
    access_token = renew_resp['access_token']

access_tokens = []
refresh_tokens = []
user_names = []

# If sim_file is set
if sim_file is not None:
    print("Reading users from file: {}".format(sim_file))

    # Fetch users from file
    with open(sim_file) as sf:
        for line in sf.readlines():
            dat = line.split(",", 3)
            user_names.append(dat[0])
            access_tokens.append(dat[2].strip())
            refresh_tokens.append(dat[1])
            if len(access_tokens) == sim_num:
                break
else:
    print("Creating users")
    # Create users
    for i in range(0, sim_num):
        uname = "stresstester-" + str(uuid.uuid4())
        resp = create_user(uname, access_token)
        if resp['status'] == 200:
            user_names.append(uname)
            access_tokens.append(resp['access_token'])
        else:
            print("Error: Create user: Bad response")


# Renew access tokens
print("Renewing access tokens")
for i in range(0, len(access_tokens)):
    renew_resp = renew_access_token(access_tokens[i], refresh_tokens[i])
    if renew_resp['status'] == 200:
        access_tokens[i] = renew_resp['access_token']


try:
    start_time = time.time()

    # Create and start all simulated users
    p = []
    for i in range(0, sim_num):
        p.append(Process(target=sim_user, args=[user_names[i], sim_duration, access_tokens[i], log_dir]))
        p[i].start()
        p[i].join(spawn_interval)


    # Clean up and terminate all sims on timeout
    for i in range(0, len(p)):
        if p[i].is_alive() and time.time() - start_time > timeout:
            p[i].terminate()

except KeyboardInterrupt:
    print("Simulation Stopped")
except TimeoutError:
    print("Simulation Done")

