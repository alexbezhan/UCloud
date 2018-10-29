library(ggplot2)

log.dir <- "log3"

logfiles <- list()
i <- 1
for(logfile in list.files(log.dir)){
    logfiles[[i]] <- read.csv(paste(log.dir, logfile, sep="/"))
    colnames(logfiles[[i]]) <- c("totalTime", "sim", "action", "status", "totalResponseTime", "responseTime", "jobid", "extra")
    i <- i + 1
}

num.sims <- length(unique(dat$sim))
total.time <- ceiling(max(dat$totalTime))

dat <- do.call("rbind", logfiles)
succdat <- dat[(dat$action != "done") & !(dat$extra == "Connection Error") & (dat$responseTime > 0),]
avg.resp.time <- mean(succdat$responseTime)

averagedat <- succdat
averagedat$totalTimeInt <- as.integer(averagedat$totalTime)

avg.resp.time.dat <- aggregate(responseTime ~ totalTimeInt, averagedat, mean)

levels(succdat$action) <- c("Create directory", "Delete directory", "Done", "List directory", "Upload file")

num.conn.err <- length(dat[(dat$extra == "Connection Error"),])
message(paste("Number of connection errors:", num.conn.err))
message(paste("Average response time:", avg.resp.time))

bp1 <- ggplot(data=succdat, aes(x=action, y=responseTime, fill=action)) +
    geom_violin(trim=FALSE) + geom_boxplot(width=0.1) +
    scale_y_log10(name="Response time (seconds)", breaks=c(0.0, 0.1, 0.2, 0.3, 0.4,0.5,0.6,0.7,0.8,0.9,1,1.5,2,2.5,3,3.5,4,4.5,5,10,15,20,30,40,50,60,70,80,90,100)) + 
    scale_x_discrete(name="Request Type") +
    scale_fill_discrete(name="Request type") +
    ggtitle(paste("Response times by Type of Request for ", num.sims, " sims over ", total.time, " seconds", sep="")) +
    coord_flip()

ggsave(filename=paste("plots/", log.dir, "_", num.sims, "_response_time_by_action.svg", sep=""), device='svg', width=60, height=30, units="cm")

lp1 <- ggplot(data=succdat[(succdat$action == "Upload file"),], aes(x=totalTime, y=responseTime, colour=action)) +
    geom_line() + 
    scale_y_continuous(name="Response time per MB (seconds)", breaks=c(0, 0.1, 0.2, 0.3, 0.4, 0.5, 1, 2, 3, 4, 5, 10, 15, 20, 25)) + 
    scale_x_continuous(name="Simulation time (seconds)") +
    theme(legend.position="none") +
    ggtitle(paste("Response times for Upload Requests for ", num.sims, " sims", sep=""))

ggsave(filename=paste("plots/", log.dir, "_", num.sims, "_upload_response_time.svg", sep=""), device='svg', width=60, height=30, units="cm")

lp2 <- ggplot(data=succdat, aes(x=totalTime, y=responseTime, colour=action)) +
    geom_line() + 
    scale_y_continuous(name="Response time (seconds)", breaks=c(0, 0.1, 0.2, 0.3, 0.4, 0.5, 1, 2, 3, 4, 5, 10, 15, 20, 25)) + 
    scale_x_continuous(name="Simulation time (seconds)") +
    ggtitle(paste("Response times for Requests for ", num.sims, " sims", sep=""))

ggsave(filename=paste("plots/", log.dir, "_", num.sims, "_line_response_time_by_action.svg", sep=""), device='svg', width=60, height=30, units="cm")

lp3 <- ggplot(data=succdat, aes(x=totalTime, y=responseTime, colour=sim)) +
    geom_line() + 
    scale_y_continuous(name="Response time (seconds)", breaks=c(0, 0.1, 0.2, 0.3, 0.4, 0.5, 1, 2, 3, 4, 5, 10, 15, 20, 25)) + 
    scale_x_continuous(name="Simulation time (seconds)") +
    ggtitle(paste("Response times for Requests for ", num.sims, " sims", sep=""))

ggsave(filename=paste("plots/", log.dir, "_", num.sims, "_line_response_time_by_sim", sep=""), device='svg', width=60, height=30, units="cm")

lp4 <- ggplot(data=succdat, aes(x=totalTime, y=responseTime)) +
    geom_line() + 
    scale_y_continuous(name="Response time (seconds)", breaks=c(0, 0.1, 0.2, 0.3, 0.4, 0.5, 1, 2, 3, 4, 5, 10, 15, 20, 25)) + 
    scale_x_continuous(name="Simulation time (seconds)") +
    ggtitle(paste("Response times for Requests for ", num.sims, " sims", sep=""))

ggsave(filename=paste("plots/", log.dir, "_", num.sims, "_line_all_response_time", sep=""), device='svg', width=60, height=30, units="cm")

lp5 <- ggplot(data=avg.resp.time.dat, aes(x=totalTimeInt, y=responseTime)) +
    geom_line() + 
    scale_y_continuous(name="Average response time (seconds)", breaks=c(0, 0.1, 0.2, 0.3, 0.4, 0.5, 1, 2, 3, 4, 5, 10, 15, 20, 25)) + 
    scale_x_continuous(name="Simulation time (seconds)") +
    ggtitle(paste("Average Response times for Requests for ", num.sims, " sims", sep=""))

ggsave(filename=paste("plots/", log.dir, "_", num.sims, "_avg_response_time", sep=""), device='svg', width=60, height=30, units="cm")







