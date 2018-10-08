# Stress Tester

## Description
Script for simulating user-activity on SDUCloud.

The script will spawn a number of processes (see `sim_num`), at an interval of `spawn_interval`, with each process acting as a simulated user. Each simulated user will take random actions until either `timeout` or `sim_duration` is reached. The current implemented actions are:

 - Create directory
 - Delete directory
 - List directory
 - Upload file

Each simulated user will hold a list (locally) of created directories, and delete from that if deciding to *Delete*. After `sim_duration` runs out, the remaining directories will be requested deleted.

Upon taking the action of *Upload file*, a random number of bytes between (1 MB and 26 MB) will be generated and uploaded as the file. 

## Run
Can be run with

    python main.py [arg=val ..]

Note: Only tested with Python 3.7

### Parameters:

 - `timeout` (int) (default = 20)
   Defines a timeout in seconds. Processes will be killed if they spend more time.

 - `sim_duration` (int) (default = 10)
   Defines how much time (in seconds) each simulated user can spend. Should be less than `timeout`, unless you don't want the simulated users to finish.

 - `spawn_interval` (int) (default = 1)
   Defines the number of second between spawning each simulated user (process).

 - `sim_num` (int) (default = 1)
   Defines the number of users to simulate (or number of processes to spawn). 

 - `log_dir` (str) (default = None)
   Name of a directory (does not have to exist). A log file for each simulated user will be written to `log_dir` before terminating. The name of the file will be the name of the simulated user, and if the file already exists it will be overwritten.



