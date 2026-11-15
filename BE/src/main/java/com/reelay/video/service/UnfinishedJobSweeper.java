package com.reelay.video.service;

import com.reelay.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 서버가 작업 도중 꺼지면 RUNNING 인 채로 아무도 안 돌리는 job 이 남고, FE 폴링은 영원히 RUNNING 을 받는다
// 기동이 끝나면 그런 job 을 SERVER_RESTARTED 로 실패 처리한다
// (서버는 살아 있는데 스레드가 멈춘 경우는 여기서 못 잡는다 -> FE 폴링 타임아웃 10분과 병행)
@Component
public class UnfinishedJobSweeper {

    private static final Logger log = LoggerFactory.getLogger(UnfinishedJobSweeper.class);

    private final JobProgressRecorder recorder;

    public UnfinishedJobSweeper(JobProgressRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sweep() {
        int count = recorder.failUnfinished(ErrorCode.SERVER_RESTARTED);
        if (count > 0) {
            log.warn("재시작 전 끝나지 않은 job {}개를 SERVER_RESTARTED 로 실패 처리했습니다", count);
        }
    }
}
