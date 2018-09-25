from multiprocessing import Process
import random
import time
import uuid

def sim_user(name, timeout):
    created_dirs = []
    interval = 1
    start_time = time.time()
    running = True

    while running:
        do = random.randint(0, 2)
        if do == 0:
            dir_name = uuid.uuid4()
            print("{}: Creating directory {}".format(name, dir_name))
            created_dirs.append(dir_name)

        time.sleep(interval)

        if time.time() - start_time >= timeout:
            running = False

    print("{}: Created {} directories".format(name, len(created_dirs)))
    print("{} done".format(name))

    return 0
