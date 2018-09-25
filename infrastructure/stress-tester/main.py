import requests
import jwt
import sys
import time
from simuser import sim_user
import os
import random
from multiprocessing import Pool, TimeoutError, Barrier, Process

########## PROGRAM PARAMETERS ##########

# Max duration in seconds. Will terminate all processes, even if they are not done.
TIMEOUT = 10

# Max duration each simulated user can spend (seconds)
SIM_DURATION = 5

# Spawn interval (seconds) between simulated users
SPAWN_INTERVAL = 1

# Number of users to simulate
SIM_USER_NUM = 3

# Will change this later
refresh_token = ""

########## END OF PARAMETERS ##########

baseUrl = "https://cloud.sdu.dk"
access_token = ''

# TODO Will fail on invalid refreshToken
def renew_access_token(atoken, rtoken):
    if rtoken == "":
        print("No refreshToken specified")
        sys.exit(-1)

    renew = False
    if atoken == "":
        renew = True
    else:
        access_jwt = jwt.decode(atoken, verify = False, algorithms=['RS256'])

        # Check if expired or expires within the next 10 seconds
        if access_jwt['exp'] <= time.time() + 10:
            renew = True 
        else:
            renew = False

    if renew:
        # Fetch new token
        res = requests.post(
            os.path.join(baseUrl, "auth", "refresh"),
            headers = {
                "Authorization": " ".join(["Bearer", rtoken])    
            }
        )

        print("Access token updated!")
        return res.json()["accessToken"]
    return atoken

# Fetch access token
access_token = renew_access_token(access_token, refresh_token)

try:
    start_time = time.time()

    # Start all simulated users
    p = []
    for i in range(0, SIM_USER_NUM):
        #sim_users[i].start()
        p.append(Process(target=sim_user, args=["sim"+str(i), SIM_DURATION]))
        p[i].start()
        p[i].join(SPAWN_INTERVAL)


    # Clean up and Terminate all on timeout
    for i in range(0, len(p)):
        if p[i].is_alive() and time.time() - start_time > TIMEOUT:
            #sim_users[i].cleanup()
            p[i].terminate()

except KeyboardInterrupt:
    print("Simulation Stopped")
except TimeoutError:
    print("Simulation Done")

# For later reference
"""files = requests.get(
    "https://cloud.sdu.dk/api/files?path=/home/brian@alberg.org/",
    headers = {
        "Authorization": " ".join(["Bearer", access_token])
    }
).json()"""


   
