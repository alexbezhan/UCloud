import requests
import jwt
import sys
import time
import re
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

# Print arguments
print("""Using arguments:
        timeout: {}
        sim_duration: {}
        spawn_interval: {}
        sim_num: {}
    """.format(timeout, sim_duration, spawn_interval, sim_num)
    )

# Will change this later
refresh_token = "1156c434-1a82-4ef8-a8ed-a16c85ae4d4d"

########## END OF PARAMETERS ##########

access_token = ''

# Fetch access token
renew_resp = renew_access_token(access_token, refresh_token)
if renew_resp['status'] == 200:
    access_token = renew_resp['access_token']

try:
    start_time = time.time()

    # Create and start all simulated users
    p = []
    for i in range(0, sim_num):
        p.append(Process(target=sim_user, args=[sim_duration, access_token]))
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

