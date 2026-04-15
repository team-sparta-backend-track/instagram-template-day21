package com.example.instagramclone.domain.post.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * [Day 19 Step 3] Spring Batch Chunk 기반 likeCount 재집계 Job.
 *
 * <p>Reader에서 LEFT JOIN + GROUP BY로 집계까지 완료하므로
 * Processor 없이 Reader → Writer 2단계로 동작한다.
 *
 * <pre>
 * Job: likeCountSyncJob
 * └── Step: likeCountSyncStep  (chunk size = 1,000)
 *     ├── ItemReader : LEFT JOIN + GROUP BY로 [postId, count] 커서 스트리밍
 *     └── ItemWriter : UPDATE posts SET like_count = ? WHERE id = ?
 * </pre>
 */
@Configuration
@RequiredArgsConstructor
public class LikeCountSyncJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Bean
    public Job likeCountSyncJob() {
        return new JobBuilder("likeCountSyncJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(likeCountSyncStep())
                .build();
    }

    @Bean
    public Step likeCountSyncStep() {
        return new StepBuilder("likeCountSyncStep", jobRepository)
                .<Object[], Object[]>chunk(5000, transactionManager)
                .reader(likeCountReader())
                .writer(likeCountWriter())
                .build();
    }

    /**
     * LEFT JOIN + GROUP BY로 [count, postId]를 커서 스트리밍한다.
     * Processor 없이 Reader가 집계까지 완료하므로 건별 COUNT(*) N번이 사라진다.
     */
    @Bean
    public JdbcCursorItemReader<Object[]> likeCountReader() {
        return new JdbcCursorItemReaderBuilder<Object[]>()
                .name("likeCountReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT p.id AS post_id, COUNT(pl.post_id) AS cnt
                        FROM posts p LEFT JOIN post_like pl ON p.id = pl.post_id
                        GROUP BY p.id
                        ORDER BY p.id
                        """)
                .rowMapper((rs, rowNum) -> new Object[]{
                        rs.getLong("cnt"),       // [0] count
                        rs.getLong("post_id")    // [1] postId
                })
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<Object[]> likeCountWriter() {
        return new JdbcBatchItemWriterBuilder<Object[]>()
                .dataSource(dataSource)
                .sql("UPDATE posts SET like_count = ? WHERE id = ?")
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setLong(1, (Long) item[0]);
                    ps.setLong(2, (Long) item[1]);
                })
                .build();
    }
}
