import requests
import time
import os
import json
import jwt
import random

baseUrl = "https://cloud.sdu.dk"

def create_directory(name, atoken):
    access_jwt = jwt.decode(atoken, verify=False)
    username = access_jwt["sub"]

    dir_path = os.path.join('/home', username, str(name))
    res = requests.post(
        os.path.join(baseUrl, "api", "files", "directory"),
        json = {
            'path': dir_path
        },
        headers = {
            "Authorization": " ".join(["Bearer", atoken])
        }
    )

    return {'status': res.status_code, 'reason': res.reason, 'ms':res.elapsed, 'job_id': res.headers['Job-Id']}

def list_directory(atoken):
    access_jwt = jwt.decode(atoken, verify=False)
    username = access_jwt["sub"]

    res = requests.get(
        os.path.join(baseUrl, 'api', 'files'),
        params = {
            'path': os.path.join("/home", username)
        },
        headers = {
            "Authorization": " ".join(["Bearer", atoken])
        }
    )

    return {'status': res.status_code, 'reason': res.reason, 'ms':res.elapsed, 'job_id': res.headers['Job-Id']}
     

def delete_directory(name, atoken):
    access_jwt = jwt.decode(atoken, verify=False)
    username = access_jwt["sub"]

    if name != "":
        dir_path = os.path.join('/home', username, str(name))
        res = requests.delete(
            os.path.join(baseUrl, "api", "files"),
            json = {
                'path': dir_path
            },
            headers = {
                "Authorization": " ".join(["Bearer", atoken])
            }
        )


    return {'status': res.status_code, 'reason': res.reason, 'ms':res.elapsed, 'job_id': res.headers['Job-Id']}


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

        atoken = res.json()["accessToken"]
        return {'access_token': atoken, 'status': res.status_code, 'reason': res.reason, 'ms':res.elapsed, 'job_id': res.headers['Job-Id']}
    else:
        return {'access_token': atoken, 'status': 200, 'reason': '', 'ms': 0, 'job_id': ''}


def create_user(name, atoken):
    res = requests.post(
        os.path.join(baseUrl, "auth", "users", "register"),
        json = {
            "username": name,
            "password": ""
        },
        headers = {
            "Authorization": " ".join(["Bearer", atoken])    
        }
    )


    if res.ok:
        user = (name, res.json()["refreshToken"], res.json()["accessToken"], res.json()["csrfToken"], '\n')
        csv_str = ','.join(user)
        with open('users.csv', 'a') as fp:
            fp.write(csv_str)

    return {'access_token': res.json()['accessToken'],
            'refresh_token': res.json()['refreshToken'],
            'status': res.status_code,
            'reason': res.reason,
            'ms': res.elapsed,
            'job_id': res.headers['Job-Id']
    }

def lookup_user(atoken):
    res = requests.post(
        os.path.join(baseUrl, "auth", "users", "lookup"),
        json = {
            "users": ["stresstester-gudrun"]
        },
        headers = {
            "Authorization": " ".join(["Bearer", atoken])    
        }
    )


# Generates a binary file and uploads it
# The generated file 'test.bin' will be between 1 and 25 Mb in size
# The file will be overwritten if multiple call occur
def upload_random(atoken):
    access_jwt = jwt.decode(atoken, verify=False)
    username = access_jwt["sub"]

    dir_path = os.path.join('/home', username, "test.bin")

    mb_size = random.randint(1, 25)

    b_file = os.urandom(1024*1000*mb_size)
    files = {'upload': ('test.bin', b_file)}
    data = { 'sensitivity': "CONFIDENTIAL", 'location': os.path.join("/home", username, "test.bin") }
 
    res = requests.post(
        os.path.join(baseUrl, "api", "upload"),
        files = files,
        data = data,
        headers = {
            "Authorization": " ".join(["Bearer", atoken])    
        }
    )

    return {'size': mb_size, 'status': res.status_code, 'reason': res.reason, 'ms': res.elapsed, 'job_id': res.headers['Job-Id']}



