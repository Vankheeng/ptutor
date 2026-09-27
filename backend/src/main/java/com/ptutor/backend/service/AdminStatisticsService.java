package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.enums.StatisticsGranularity;
import com.ptutor.backend.dto.response.StatisticsBucketResponse;
import com.ptutor.backend.dto.response.StatisticsMetricResponse;
import com.ptutor.backend.dto.response.StatisticsRangeResponse;
import com.ptutor.backend.dto.response.StatisticsReportResponse;
import com.ptutor.backend.dto.response.StatisticsSeriesPointResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.AdminStatisticsQueryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    private static final int MAX_RANGE_DAYS = 731;
    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    private final AdminStatisticsQueryRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StatisticsReportResponse overview(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  (select coalesce(sum(amount), 0) from payments
                    where deleted_at is null and paid_at >= :from and paid_at < :to
                      and payment_type in ('STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE')
                      and status in ('PAID', 'REFUNDED')) as platform_revenue_gross,
                  (select coalesce(sum(amount), 0) from payments
                    where deleted_at is null and paid_at >= :from and paid_at < :to
                      and payment_type = 'TUITION_PAYMENT' and status in ('PAID', 'REFUNDED')) as tuition_volume,
                  (select count(*) from payments where deleted_at is null and paid_at >= :from and paid_at < :to
                      and status in ('PAID', 'REFUNDED')) as successful_payments,
                  (select count(*) from students where deleted_at is null and created_at >= :from and created_at < :to)
                      as new_students,
                  (select count(*) from tutors where deleted_at is null and created_at >= :from and created_at < :to)
                      as new_tutors,
                  (select count(*) from tutors where deleted_at is null and profile_status = 'VERIFIED'
                      and profile_reviewed_at >= :from and profile_reviewed_at < :to) as verified_tutors,
                  (select count(*) from studying_requests where deleted_at is null
                      and created_at >= :from and created_at < :to) as studying_requests,
                  (select count(*) from teaching_requests where deleted_at is null
                      and created_at >= :from and created_at < :to) as teaching_requests,
                  ((select count(*) from student_tutor_requests where deleted_at is null and status = 'ACCEPTED'
                      and created_at >= :from and created_at < :to)
                   + (select count(*) from tutor_student_requests where deleted_at is null and status = 'ACCEPTED'
                      and created_at >= :from and created_at < :to)) as accepted_matches,
                  (select count(*) from contracts where deleted_at is null
                      and signed_at >= :from and signed_at < :to) as signed_contracts,
                  (select count(*) from lessons where deleted_at is null and status = 'COMPLETED'
                      and date >= :fromDate and date < :toDateExclusive) as completed_lessons,
                  (select count(*) from withdrawal_requests where deleted_at is null
                      and status in ('PENDING', 'PROCESSING')) as pending_withdrawals
                """;
        Map<String, String> series = new LinkedHashMap<>();
        series.put("platformRevenue", paymentSeries(
                "payment_type in ('STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE')", "sum(amount)"));
        series.put("signedContracts", timestampSeries("contracts", "signed_at", "count(*)", "signed_at is not null"));
        series.put("newStudents", timestampSeries("students", "created_at", "count(*)", "true"));
        series.put("newTutors", timestampSeries("tutors", "created_at", "count(*)", "true"));
        series.put("completedLessons", lessonDateSeries("count(*)", "status = 'COMPLETED'"));
        return report("OVERVIEW", context, metrics, Map.of(), series);
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse students(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  (select count(*) from students where deleted_at is null and created_at < :to) as total_students,
                  (select count(*) from students where deleted_at is null
                      and created_at >= :from and created_at < :to) as new_students,
                  (select count(distinct sr.student_id) from studying_requests sr
                      where sr.deleted_at is null and sr.created_at >= :from and sr.created_at < :to)
                      as students_with_studying_request,
                  (select count(distinct str.student_id) from student_tutor_requests str
                      where str.deleted_at is null and str.created_at >= :from and str.created_at < :to)
                      as students_sending_offer,
                  (select count(distinct c.student_id) from contracts c where c.deleted_at is null
                      and c.signed_at >= :from and c.signed_at < :to) as students_with_signed_contract,
                  (select count(distinct c.student_id) from lessons l join contracts c on c.id = l.contract_id
                      where l.deleted_at is null and c.deleted_at is null and l.status = 'COMPLETED'
                      and l.date >= :fromDate and l.date < :toDateExclusive) as students_with_completed_lesson,
                  (select count(distinct c.student_id) from payments p join contracts c on c.id = p.reference_id
                      where p.deleted_at is null and c.deleted_at is null and p.reference_type = 'CONTRACT'
                      and p.payment_type = 'TUITION_PAYMENT' and p.status in ('PAID', 'REFUNDED')
                      and p.paid_at >= :from and p.paid_at < :to) as paying_students,
                  (select coalesce(sum(p.amount), 0) from payments p where p.deleted_at is null
                      and p.payment_type = 'TUITION_PAYMENT' and p.status in ('PAID', 'REFUNDED')
                      and p.paid_at >= :from and p.paid_at < :to) as tuition_volume
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("accountStatus", """
                select u.status as bucket, count(*) as value from students s join users u on u.id = s.user_id
                where s.deleted_at is null and u.deleted_at is null group by u.status order by u.status
                """);
        breakdowns.put("gender", """
                select coalesce(u.gender, 'UNSPECIFIED') as bucket, count(*) as value
                from students s join users u on u.id = s.user_id
                where s.deleted_at is null and u.deleted_at is null group by u.gender order by bucket
                """);
        breakdowns.put("ageGroup", """
                select case
                    when u.date_of_birth is null then 'UNSPECIFIED'
                    when extract(year from age(current_date, u.date_of_birth)) < 13 then 'UNDER_13'
                    when extract(year from age(current_date, u.date_of_birth)) < 18 then '13_17'
                    when extract(year from age(current_date, u.date_of_birth)) < 25 then '18_24'
                    when extract(year from age(current_date, u.date_of_birth)) < 35 then '25_34'
                    else '35_PLUS' end as bucket, count(*) as value
                from students s join users u on u.id = s.user_id
                where s.deleted_at is null and u.deleted_at is null group by bucket order by bucket
                """);
        breakdowns.put("province", """
                select coalesce(p.name, 'UNSPECIFIED') as bucket, count(*) as value
                from students s join users u on u.id = s.user_id
                left join districts d on d.id = u.district_id left join provinces p on p.id = d.province_id
                where s.deleted_at is null and u.deleted_at is null group by p.name order by value desc, bucket
                """);
        return report("STUDENTS", context, metrics, breakdowns,
                Map.of("registrations", timestampSeries("students", "created_at", "count(*)", "true")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse tutors(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  (select count(*) from tutors where deleted_at is null and created_at < :to) as total_tutors,
                  (select count(*) from tutors where deleted_at is null
                      and created_at >= :from and created_at < :to) as new_tutors,
                  (select count(*) from tutors where deleted_at is null and profile_status = 'VERIFIED')
                      as verified_tutors,
                  (select count(distinct tutor_id) from contracts where deleted_at is null
                      and signed_at >= :from and signed_at < :to) as tutors_with_signed_contract,
                  (select count(distinct tutor_id) from contracts where deleted_at is null and status = 'ACTIVE')
                      as tutors_with_active_contract,
                  (select coalesce(avg(average_rating), 0) from tutors where deleted_at is null) as average_rating,
                  (select coalesce(avg(recommendation_score), 0) from tutors where deleted_at is null)
                      as average_recommendation_score,
                  (select count(distinct c.tutor_id) from lessons l join contracts c on c.id = l.contract_id
                      where l.deleted_at is null and c.deleted_at is null and l.status = 'COMPLETED'
                      and l.date >= :fromDate and l.date < :toDateExclusive) as tutors_with_completed_lesson,
                  (select coalesce(sum(p.amount), 0) from payments p
                      join contract_payment_installments i on i.id = p.payment_installment_id
                      join contracts c on c.id = i.contract_id
                      where p.deleted_at is null and i.deleted_at is null and c.deleted_at is null
                      and p.status in ('PAID', 'REFUNDED') and p.payment_type = 'TUITION_PAYMENT'
                      and p.paid_at >= :from and p.paid_at < :to) as tuition_volume
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("profileStatus", """
                select profile_status as bucket, count(*) as value from tutors
                where deleted_at is null group by profile_status order by profile_status
                """);
        breakdowns.put("accountStatus", """
                select u.status as bucket, count(*) as value from tutors t join users u on u.id = t.user_id
                where t.deleted_at is null and u.deleted_at is null group by u.status order by u.status
                """);
        breakdowns.put("experience", """
                select case
                    when experience_years is null then 'UNSPECIFIED'
                    when experience_years < 2 then 'UNDER_2_YEARS'
                    when experience_years < 5 then '2_4_YEARS'
                    when experience_years < 10 then '5_9_YEARS'
                    else '10_PLUS_YEARS' end as bucket, count(*) as value
                from tutors where deleted_at is null group by bucket order by bucket
                """);
        breakdowns.put("province", """
                select coalesce(p.name, 'UNSPECIFIED') as bucket, count(*) as value
                from tutors t join users u on u.id = t.user_id
                left join districts d on d.id = u.district_id left join provinces p on p.id = d.province_id
                where t.deleted_at is null and u.deleted_at is null group by p.name order by value desc, bucket
                """);
        return report("TUTORS", context, metrics, breakdowns,
                Map.of("registrations", timestampSeries("tutors", "created_at", "count(*)", "true")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse studyingRequests(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        return requestReport("STUDYING_REQUESTS", "studying_requests", "min_price", "max_price",
                fromDate, toDate, timezone, granularity);
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse teachingRequests(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        return requestReport("TEACHING_REQUESTS", "teaching_requests", "expected_price", "expected_price",
                fromDate, toDate, timezone, granularity);
    }

    private StatisticsReportResponse requestReport(
            String name, String table, String minimumPriceColumn, String maximumPriceColumn,
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = "select count(*) as created_requests, "
                + "coalesce(avg(" + minimumPriceColumn + "), 0) as average_minimum_price, "
                + "coalesce(avg(" + maximumPriceColumn + "), 0) as average_maximum_price, "
                + "count(*) filter (where status = 'MATCHED') as matched_requests, "
                + "count(*) filter (where status = 'CANCELLED') as cancelled_requests "
                + "from " + table + " where deleted_at is null and created_at >= :from and created_at < :to";
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", "select status as bucket, count(*) as value from " + table
                + " where deleted_at is null group by status order by status");
        String modeColumn = table.equals("studying_requests") ? "learning_mode" : "teaching_mode";
        breakdowns.put("mode", "select " + modeColumn + " as bucket, count(*) as value from " + table
                + " where deleted_at is null and created_at >= :from and created_at < :to group by " + modeColumn
                + " order by " + modeColumn);
        if (table.equals("studying_requests")) {
            breakdowns.put("subject", """
                    select s.name as bucket, count(*) as value from studying_requests r
                    join subjects s on s.id = r.subject_id where r.deleted_at is null
                    and r.created_at >= :from and r.created_at < :to group by s.name order by value desc, s.name
                    """);
            breakdowns.put("grade", """
                    select g.name as bucket, count(*) as value from studying_requests r
                    join grades g on g.id = r.grade_id where r.deleted_at is null
                    and r.created_at >= :from and r.created_at < :to group by g.name order by value desc, g.name
                    """);
            breakdowns.put("province", """
                    select coalesce(p.name, 'UNSPECIFIED') as bucket, count(*) as value from studying_requests r
                    left join districts d on d.id = r.district_id left join provinces p on p.id = d.province_id
                    where r.deleted_at is null and r.created_at >= :from and r.created_at < :to
                    group by p.name order by value desc, bucket
                    """);
        } else {
            breakdowns.put("subject", """
                    select coalesce(s.name, r.custom_subject_name, 'UNSPECIFIED') as bucket, count(*) as value
                    from teaching_requests r left join subjects s on s.id = r.subject_id
                    where r.deleted_at is null and r.created_at >= :from and r.created_at < :to
                    group by coalesce(s.name, r.custom_subject_name, 'UNSPECIFIED') order by value desc, bucket
                    """);
            breakdowns.put("grade", """
                    select g.name as bucket, count(*) as value from teaching_requests r
                    join grade_teaching_requests gr on gr.teaching_request_id = r.id and gr.deleted_at is null
                    join grades g on g.id = gr.grade_id where r.deleted_at is null
                    and r.created_at >= :from and r.created_at < :to group by g.name order by value desc, g.name
                    """);
        }
        return report(name, context, metrics, breakdowns,
                Map.of("created", timestampSeries(table, "created_at", "count(*)", "true")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse matching(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String union = """
                select created_at, status, proposed_price, 'STUDENT_INITIATED' as direction
                from student_tutor_requests where deleted_at is null
                union all
                select created_at, status, proposed_price, 'TUTOR_INITIATED' as direction
                from tutor_student_requests where deleted_at is null
                """;
        String metrics = "select count(*) as offers, "
                + "count(*) filter (where status = 'ACCEPTED') as accepted_offers, "
                + "count(*) filter (where status = 'REJECTED') as rejected_offers, "
                + "count(*) filter (where status = 'CANCELLED') as cancelled_offers, "
                + "coalesce(100.0 * count(*) filter (where status = 'ACCEPTED') / nullif(count(*), 0), 0) "
                + "as acceptance_rate, "
                + "coalesce(avg(proposed_price), 0) as average_proposed_price from (" + union + ") x "
                + "where created_at >= :from and created_at < :to";
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", "select status as bucket, count(*) as value from (" + union
                + ") x where created_at >= :from and created_at < :to group by status order by status");
        breakdowns.put("direction", "select direction as bucket, count(*) as value from (" + union
                + ") x where created_at >= :from and created_at < :to group by direction order by direction");
        String series = "select " + bucket("created_at", context.granularity())
                + " as period, count(*) as value from (" + union + ") x "
                + "where created_at >= :from and created_at < :to group by period order by period";
        return report("MATCHING", context, metrics, breakdowns, Map.of("offers", series));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse contracts(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  count(*) filter (where created_at >= :from and created_at < :to) as created_contracts,
                  count(*) filter (where signed_at >= :from and signed_at < :to) as signed_contracts,
                  count(*) filter (where created_at >= :from and created_at < :to and status = 'PENDING')
                      as pending_contracts,
                  count(*) filter (where created_at >= :from and created_at < :to and status = 'ACTIVE')
                      as active_contracts,
                  count(*) filter (where created_at >= :from and created_at < :to and status = 'COMPLETED')
                      as completed_contracts,
                  count(*) filter (where created_at >= :from and created_at < :to and status = 'CANCELLED')
                      as cancelled_contracts,
                  coalesce(avg(price) filter (where created_at >= :from and created_at < :to), 0)
                      as average_price,
                  coalesce(avg(total_lession) filter (where created_at >= :from and created_at < :to), 0)
                      as average_lessons,
                  count(*) filter (where renewed_from_contract_id is not null
                      and created_at >= :from and created_at < :to) as renewed_contracts,
                  coalesce(100.0 * count(*) filter (where signed_at >= :from and signed_at < :to)
                      / nullif(count(*) filter (where created_at >= :from and created_at < :to), 0), 0)
                      as signing_rate
                from contracts where deleted_at is null
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", """
                select status as bucket, count(*) as value from contracts where deleted_at is null
                group by status order by status
                """);
        breakdowns.put("teachingMode", """
                select teaching_mode as bucket, count(*) as value from contracts where deleted_at is null
                and signed_at >= :from and signed_at < :to group by teaching_mode order by teaching_mode
                """);
        breakdowns.put("paymentPeriod", """
                select payment_period as bucket, count(*) as value from contracts where deleted_at is null
                and signed_at >= :from and signed_at < :to group by payment_period order by payment_period
                """);
        breakdowns.put("subject", """
                select s.name as bucket, count(*) as value from contracts c join subjects s on s.id = c.subject_id
                where c.deleted_at is null and c.signed_at >= :from and c.signed_at < :to
                group by s.name order by value desc, s.name
                """);
        breakdowns.put("grade", """
                select g.name as bucket, count(*) as value from contracts c join grades g on g.id = c.grade_id
                where c.deleted_at is null and c.signed_at >= :from and c.signed_at < :to
                group by g.name order by value desc, g.name
                """);
        Map<String, String> series = new LinkedHashMap<>();
        series.put("created", timestampSeries("contracts", "created_at", "count(*)", "true"));
        series.put("signed", timestampSeries("contracts", "signed_at", "count(*)", "signed_at is not null"));
        return report("CONTRACTS", context, metrics, breakdowns, series);
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse lessons(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  count(*) as scheduled_lessons,
                  count(*) filter (where status = 'COMPLETED') as completed_lessons,
                  count(*) filter (where status = 'CANCELLED') as cancelled_lessons,
                  count(*) filter (where status = 'PENDING_CONFIRMATION') as pending_confirmation_lessons,
                  coalesce(100.0 * count(*) filter (where status = 'COMPLETED') / nullif(count(*), 0), 0)
                      as completion_rate,
                  coalesce(100.0 * count(*) filter (where status = 'CANCELLED') / nullif(count(*), 0), 0)
                      as cancellation_rate,
                  coalesce(sum(extract(epoch from (end_time - start_time)) / 3600), 0) as scheduled_hours,
                  coalesce(sum(extract(epoch from (end_time - start_time)) / 3600)
                      filter (where status = 'COMPLETED'), 0) as completed_hours,
                  coalesce(avg(extract(epoch from (end_time - start_time)) / 60), 0) as average_duration_minutes,
                  count(*) filter (where date < :toDateExclusive and date < current_date
                      and status = 'SCHEDULED') as overdue_scheduled_lessons
                from lessons where deleted_at is null and date >= :fromDate and date < :toDateExclusive
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", """
                select status as bucket, count(*) as value from lessons where deleted_at is null
                and date >= :fromDate and date < :toDateExclusive group by status order by status
                """);
        breakdowns.put("teachingMode", """
                select teaching_mode as bucket, count(*) as value from lessons where deleted_at is null
                and date >= :fromDate and date < :toDateExclusive group by teaching_mode order by teaching_mode
                """);
        breakdowns.put("dayOfWeek", """
                select trim(to_char(date, 'Day')) as bucket, count(*) as value from lessons
                where deleted_at is null and date >= :fromDate and date < :toDateExclusive
                group by extract(isodow from date), trim(to_char(date, 'Day')) order by extract(isodow from date)
                """);
        breakdowns.put("subject", """
                select s.name as bucket, count(*) as value from lessons l
                join contracts c on c.id = l.contract_id join subjects s on s.id = c.subject_id
                where l.deleted_at is null and c.deleted_at is null
                and l.date >= :fromDate and l.date < :toDateExclusive
                group by s.name order by value desc, s.name
                """);
        return report("LESSONS", context, metrics, breakdowns,
                Map.of("lessons", lessonDateSeries("count(*)", "true"),
                        "completed", lessonDateSeries("count(*)", "status = 'COMPLETED'")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse finance(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  coalesce(sum(amount) filter (where payment_type in ('STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE')
                      and status in ('PAID', 'REFUNDED')), 0) as platform_revenue_gross,
                  coalesce(sum(amount) filter (where payment_type in ('STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE')
                      and status = 'REFUNDED'), 0) as platform_fees_currently_refunded,
                  coalesce(sum(amount) filter (where payment_type in ('STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE')
                      and status = 'PAID'), 0) as platform_revenue_current_net,
                  coalesce(sum(amount) filter (where payment_type = 'TUITION_PAYMENT'
                      and status in ('PAID', 'REFUNDED')), 0) as tuition_volume,
                  count(*) filter (where status in ('PAID', 'REFUNDED')) as successful_payments,
                  coalesce(avg(amount) filter (where status in ('PAID', 'REFUNDED')), 0)
                      as average_successful_payment
                from payments where deleted_at is null and paid_at >= :from and paid_at < :to
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("paymentType", paidPaymentBreakdown("payment_type"));
        breakdowns.put("paymentMethod", paidPaymentBreakdown("payment_method"));
        Map<String, String> series = new LinkedHashMap<>();
        series.put("platformRevenue", paymentSeries(
                "payment_type in ('STUDYING_REQUEST_FEE', 'TEACHING_REQUEST_FEE')", "sum(amount)"));
        series.put("tuitionVolume", paymentSeries("payment_type = 'TUITION_PAYMENT'", "sum(amount)"));
        return report("FINANCE", context, metrics, breakdowns, series);
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse payments(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  count(*) as initiated_payments,
                  count(*) filter (where status in ('PAID', 'REFUNDED')) as successful_payments,
                  count(*) filter (where status = 'FAILED') as failed_payments,
                  count(*) filter (where status = 'PENDING') as pending_payments,
                  count(*) filter (where status = 'REFUNDED') as currently_refunded_payments,
                  coalesce(100.0 * count(*) filter (where status in ('PAID', 'REFUNDED'))
                      / nullif(count(*), 0), 0) as success_rate,
                  coalesce(sum(amount), 0) as initiated_value,
                  coalesce(avg(amount), 0) as average_payment_value
                from payments where deleted_at is null and created_at >= :from and created_at < :to
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", createdPaymentBreakdown("status"));
        breakdowns.put("paymentType", createdPaymentBreakdown("payment_type"));
        breakdowns.put("paymentMethod", createdPaymentBreakdown("payment_method"));
        return report("PAYMENTS", context, metrics, breakdowns,
                Map.of("initiated", timestampSeries("payments", "created_at", "count(*)", "true"),
                        "successful", timestampSeries("payments", "paid_at", "count(*)",
                                "status in ('PAID', 'REFUNDED')")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse installments(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  count(*) as installments,
                  count(*) filter (where status = 'PAID') as paid_installments,
                  count(*) filter (where status = 'PENDING') as pending_installments,
                  count(*) filter (where status = 'CANCELLED') as cancelled_installments,
                  count(*) filter (where status = 'PENDING' and due_date < current_date) as overdue_installments,
                  coalesce(sum(amount), 0) as installment_value,
                  coalesce(sum(amount) filter (where status = 'PAID'), 0) as paid_value,
                  coalesce(sum(amount) filter (where status = 'PENDING'), 0) as pending_value,
                  coalesce(sum(amount) filter (where status = 'PENDING' and due_date < current_date), 0)
                      as overdue_value,
                  coalesce(100.0 * count(*) filter (where status = 'PAID') / nullif(count(*), 0), 0)
                      as collection_rate
                from contract_payment_installments
                where deleted_at is null and created_at >= :from and created_at < :to
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", """
                select status as bucket, count(*) as value from contract_payment_installments
                where deleted_at is null and created_at >= :from and created_at < :to
                group by status order by status
                """);
        breakdowns.put("paymentPeriod", """
                select payment_period as bucket, count(*) as value from contract_payment_installments
                where deleted_at is null and created_at >= :from and created_at < :to
                group by payment_period order by payment_period
                """);
        return report("INSTALLMENTS", context, metrics, breakdowns,
                Map.of("created", timestampSeries("contract_payment_installments", "created_at", "count(*)", "true")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse wallets(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  (select count(*) from wallets where deleted_at is null) as wallets,
                  (select coalesce(sum(balance), 0) from wallets where deleted_at is null) as available_balance,
                  (select coalesce(sum(pending_balance), 0) from wallets where deleted_at is null) as pending_balance,
                  count(*) as transactions,
                  coalesce(sum(amount) filter (where transaction_type = 'CREDIT'), 0) as credit_value,
                  coalesce(sum(amount) filter (where transaction_type = 'DEBIT'), 0) as debit_value,
                  count(*) filter (where status = 'COMPLETED') as completed_transactions,
                  count(*) filter (where status = 'PENDING') as pending_transactions
                from wallet_transactions where deleted_at is null and created_at >= :from and created_at < :to
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("transactionType", """
                select transaction_type as bucket, coalesce(sum(amount), 0) as value from wallet_transactions
                where deleted_at is null and created_at >= :from and created_at < :to
                group by transaction_type order by transaction_type
                """);
        breakdowns.put("purpose", """
                select purpose as bucket, coalesce(sum(amount), 0) as value from wallet_transactions
                where deleted_at is null and created_at >= :from and created_at < :to
                group by purpose order by purpose
                """);
        breakdowns.put("status", """
                select status as bucket, count(*) as value from wallet_transactions
                where deleted_at is null and created_at >= :from and created_at < :to
                group by status order by status
                """);
        return report("WALLETS", context, metrics, breakdowns,
                Map.of("transactions", timestampSeries("wallet_transactions", "created_at", "count(*)", "true")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse withdrawals(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  count(*) as withdrawal_requests,
                  coalesce(sum(amount), 0) as requested_value,
                  count(*) filter (where status = 'COMPLETED') as completed_withdrawals,
                  coalesce(sum(amount) filter (where status = 'COMPLETED'), 0) as completed_value,
                  count(*) filter (where status in ('PENDING', 'PROCESSING')) as pending_withdrawals,
                  coalesce(100.0 * count(*) filter (where status = 'COMPLETED') / nullif(count(*), 0), 0)
                      as completion_rate,
                  coalesce(avg(amount), 0) as average_withdrawal,
                  coalesce(avg(extract(epoch from (reviewed_at - created_at)) / 3600)
                      filter (where reviewed_at is not null), 0) as average_review_hours,
                  coalesce(avg(extract(epoch from (completed_at - created_at)) / 3600)
                      filter (where completed_at is not null), 0) as average_completion_hours
                from withdrawal_requests where deleted_at is null and created_at >= :from and created_at < :to
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("status", """
                select status as bucket, count(*) as value from withdrawal_requests
                where deleted_at is null and created_at >= :from and created_at < :to
                group by status order by status
                """);
        breakdowns.put("bank", """
                select bank_code as bucket, count(*) as value from withdrawal_requests
                where deleted_at is null and created_at >= :from and created_at < :to
                group by bank_code order by value desc, bank_code
                """);
        return report("WITHDRAWALS", context, metrics, breakdowns,
                Map.of("requests", timestampSeries("withdrawal_requests", "created_at", "count(*)", "true")));
    }

    @Transactional(readOnly = true)
    public StatisticsReportResponse employees(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ReportContext context = context(fromDate, toDate, timezone, granularity);
        String metrics = """
                select
                  (select count(*) from employees where deleted_at is null) as employees,
                  (select count(*) from employees e join users u on u.id = e.user_id
                      where e.deleted_at is null and u.deleted_at is null and u.status = 'ACTIVE') as active_employees,
                  (select count(*) from teaching_requests where deleted_at is null and reviewed_at >= :from
                      and reviewed_at < :to) as reviewed_teaching_requests,
                  (select count(*) from tutors where deleted_at is null and profile_reviewed_at >= :from
                      and profile_reviewed_at < :to) as reviewed_tutor_profiles,
                  (select count(*) from withdrawal_requests where deleted_at is null and reviewed_at >= :from
                      and reviewed_at < :to) as reviewed_withdrawals
                """;
        Map<String, String> breakdowns = new LinkedHashMap<>();
        breakdowns.put("jobFunction", """
                select coalesce(job_function, 'UNASSIGNED') as bucket, count(*) as value from employees
                where deleted_at is null group by job_function order by bucket
                """);
        breakdowns.put("accountStatus", """
                select u.status as bucket, count(*) as value from employees e join users u on u.id = e.user_id
                where e.deleted_at is null and u.deleted_at is null group by u.status order by u.status
                """);
        return report("EMPLOYEES", context, metrics, breakdowns, Map.of());
    }

    private StatisticsReportResponse report(
            String name,
            ReportContext context,
            String metricsSql,
            Map<String, String> breakdownSql,
            Map<String, String> seriesSql) {
        String resolvedMetricsSql = withGranularity(metricsSql, context.granularity());
        Map<String, BigDecimal> current = repository.metrics(resolvedMetricsSql, parameters(context, false));
        Map<String, BigDecimal> previous = repository.metrics(resolvedMetricsSql, parameters(context, true));
        Map<String, StatisticsMetricResponse> metrics = compare(current, previous);

        Map<String, List<StatisticsBucketResponse>> breakdowns = new LinkedHashMap<>();
        breakdownSql.forEach((key, sql) -> breakdowns.put(key,
                repository.breakdown(withGranularity(sql, context.granularity()), parameters(context, false))));

        Map<String, List<StatisticsSeriesPointResponse>> series = new LinkedHashMap<>();
        seriesSql.forEach((key, sql) -> series.put(key,
                repository.series(withGranularity(sql, context.granularity()), parameters(context, false))));

        return new StatisticsReportResponse(
                name,
                new StatisticsRangeResponse(
                        context.fromDate(), context.toDate(), context.zone().getId(), context.granularity(),
                        context.previousFromDate(), context.previousToDate()),
                metrics,
                breakdowns,
                series,
                clock.instant());
    }

    private Map<String, StatisticsMetricResponse> compare(
            Map<String, BigDecimal> current, Map<String, BigDecimal> previous) {
        Map<String, StatisticsMetricResponse> result = new LinkedHashMap<>();
        current.forEach((key, value) -> {
            BigDecimal previousValue = previous.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal change = previousValue.signum() == 0
                    ? null
                    : value.subtract(previousValue)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(previousValue.abs(), 2, RoundingMode.HALF_UP);
            result.put(key, new StatisticsMetricResponse(value, previousValue, change));
        });
        return result;
    }

    private ReportContext context(
            LocalDate fromDate, LocalDate toDate, String timezone, StatisticsGranularity granularity) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.strip());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATISTICS_TIMEZONE", "Timezone is invalid");
        }
        LocalDate effectiveTo = toDate == null ? LocalDate.now(clock.withZone(zone)) : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.minusDays(29) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATISTICS_DATE_RANGE",
                    "fromDate must not be after toDate");
        }
        long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATISTICS_DATE_RANGE_TOO_LARGE",
                    "Statistics date range must not exceed 731 days");
        }
        StatisticsGranularity effectiveGranularity = granularity == null ? automaticGranularity(days) : granularity;
        LocalDate previousTo = effectiveFrom.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        return new ReportContext(effectiveFrom, effectiveTo, previousFrom, previousTo, zone, effectiveGranularity);
    }

    private StatisticsGranularity automaticGranularity(long days) {
        if (days <= 31) {
            return StatisticsGranularity.DAY;
        }
        if (days <= 180) {
            return StatisticsGranularity.WEEK;
        }
        return StatisticsGranularity.MONTH;
    }

    private MapSqlParameterSource parameters(ReportContext context, boolean previous) {
        LocalDate fromDate = previous ? context.previousFromDate() : context.fromDate();
        LocalDate toDate = previous ? context.previousToDate() : context.toDate();
        LocalDateTime from = fromDate.atStartOfDay(context.zone())
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = toDate.plusDays(1).atStartOfDay(context.zone())
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("fromDate", fromDate)
                .addValue("toDateExclusive", toDate.plusDays(1))
                .addValue("timezone", context.zone().getId());
    }

    private String timestampSeries(String table, String column, String aggregate, String condition) {
        return "select " + bucket(column, null) + " as period, " + aggregate + " as value from " + table
                + " where deleted_at is null and " + column + " >= :from and " + column + " < :to and " + condition
                + " group by period order by period";
    }

    private String lessonDateSeries(String aggregate, String condition) {
        return "select date_trunc('day', date)::date as period, " + aggregate + " as value from lessons "
                + "where deleted_at is null and date >= :fromDate and date < :toDateExclusive and " + condition
                + " group by period order by period";
    }

    private String paymentSeries(String condition, String aggregate) {
        return "select " + bucket("paid_at", null) + " as period, " + aggregate + " as value from payments "
                + "where deleted_at is null and paid_at >= :from and paid_at < :to "
                + "and status in ('PAID', 'REFUNDED') and " + condition + " group by period order by period";
    }

    private String paidPaymentBreakdown(String column) {
        return "select " + column + " as bucket, coalesce(sum(amount), 0) as value from payments "
                + "where deleted_at is null and paid_at >= :from and paid_at < :to "
                + "and status in ('PAID', 'REFUNDED') group by " + column + " order by " + column;
    }

    private String createdPaymentBreakdown(String column) {
        return "select " + column + " as bucket, count(*) as value from payments "
                + "where deleted_at is null and created_at >= :from and created_at < :to "
                + "group by " + column + " order by " + column;
    }

    private String bucket(String column, StatisticsGranularity ignored) {
        // The SQL value comes from a closed enum, never from raw request input.
        return "date_trunc('day', " + column + " at time zone 'UTC' at time zone :timezone)::date";
    }

    private String withGranularity(String sql, StatisticsGranularity granularity) {
        return sql.replace("date_trunc('day',", "date_trunc('" + granularity.sqlValue() + "',");
    }

    private record ReportContext(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate previousFromDate,
            LocalDate previousToDate,
            ZoneId zone,
            StatisticsGranularity granularity) {
    }
}
