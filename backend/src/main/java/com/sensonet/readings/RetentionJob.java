package com.sensonet.readings;

import com.sensonet.config.SensonetProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Borra el detalle antiguo cuando no hay TimescaleDB (allí lo hace add_retention_policy). */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private final ReadingRepository readings;
    private final SensonetProperties props;
    private final Clock clock;

    public RetentionJob(ReadingRepository readings, SensonetProperties props, Clock clock) {
        this.readings = readings;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 * * * *")
    public void purge() {
        if (props.db().timescale()) {
            return;
        }
        int deleted = readings.deleteOlderThan(clock.instant().minus(props.db().rawRetention()));
        if (deleted > 0) {
            log.info("Retención: {} lecturas antiguas eliminadas", deleted);
        }
    }
}
