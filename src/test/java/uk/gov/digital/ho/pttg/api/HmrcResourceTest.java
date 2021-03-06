package uk.gov.digital.ho.pttg.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import net.logstash.logback.marker.ObjectAppendingMarker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.digital.ho.pttg.application.NinoUtils;
import uk.gov.digital.ho.pttg.application.domain.IncomeSummary;
import uk.gov.digital.ho.pttg.application.domain.Individual;

import java.time.LocalDate;

import static java.time.Month.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HmrcResourceTest {

    private static final String NINO = "QQ123456C";
    private static final String LAST_NAME = "LastName";
    private static final String FIRST_NAME = "FirstName";
    private static final LocalDate TO_DATE = LocalDate.of(2018, MAY, 1);
    private static final LocalDate FROM_DATE = LocalDate.of(2018, JANUARY, 1);
    private static final LocalDate DATE_OF_BIRTH = LocalDate.of(1990, DECEMBER, 25);
    private static final String ALIAS_SURNAMES = "Alias Surnames";

    @Mock private IncomeSummaryService mockIncomeSummaryService;
    @Mock private NinoUtils mockNinoUtils;
    @Mock private IncomeSummary mockIncomeSummary;
    @Mock private RequestHeaderData mockRequestHeaderData;
    @Mock private Appender<ILoggingEvent> mockAppender;

    @Captor private ArgumentCaptor<Individual> captorIndividual;

    private HmrcResource hmrcResource;

    @Before
    public void setup() {
        hmrcResource = new HmrcResource(mockIncomeSummaryService, mockNinoUtils, mockRequestHeaderData);
        when(mockIncomeSummaryService.getIncomeSummary(eq(new Individual(FIRST_NAME, LAST_NAME, NINO, DATE_OF_BIRTH, ALIAS_SURNAMES)), eq(FROM_DATE), eq(TO_DATE))).thenReturn(mockIncomeSummary);
        when(mockNinoUtils.sanitise(NINO)).thenReturn(NINO);

        Logger rootLogger = (Logger) LoggerFactory.getLogger(HmrcResource.class);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(mockAppender);
    }

    @Test
    public void shouldUseCollaborators() {

        hmrcResource.getHmrcData(new IncomeDataRequest(FIRST_NAME, LAST_NAME, NINO, DATE_OF_BIRTH, FROM_DATE, TO_DATE, ALIAS_SURNAMES));

        verify(mockNinoUtils).sanitise(NINO);
        verify(mockIncomeSummaryService).getIncomeSummary(captorIndividual.capture(), eq(FROM_DATE), eq(TO_DATE));
        verify(mockRequestHeaderData).calculateRequestDuration();

        assertThat(captorIndividual.getValue().getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(captorIndividual.getValue().getLastName()).isEqualTo(LAST_NAME);
        assertThat(captorIndividual.getValue().getNino()).isEqualTo(NINO);
        assertThat(captorIndividual.getValue().getDateOfBirth()).isEqualTo(DATE_OF_BIRTH);
        assertThat(captorIndividual.getValue().getAliasSurnames()).isEqualTo(ALIAS_SURNAMES);
    }

    @Test
    public void shouldProduceIncomeSummary() {
        IncomeSummary actualIncomeSummary = hmrcResource.getHmrcData(new IncomeDataRequest(FIRST_NAME, LAST_NAME, NINO, DATE_OF_BIRTH, FROM_DATE, TO_DATE, ALIAS_SURNAMES));

        assertThat(actualIncomeSummary).isEqualTo(mockIncomeSummary);
    }

    @Test
    public void shouldLogWhenRequestReceived() {

        hmrcResource.getHmrcData(new IncomeDataRequest(FIRST_NAME, LAST_NAME, NINO, DATE_OF_BIRTH, FROM_DATE, TO_DATE, ALIAS_SURNAMES));

        verify(mockAppender).doAppend(argThat(argument -> {
            LoggingEvent loggingEvent = (LoggingEvent) argument;

            return loggingEvent.getFormattedMessage().equals("Hmrc service invoked for nino QQ123456C with date range 2018-01-01 to 2018-05-01") &&
                           ((ObjectAppendingMarker) loggingEvent.getArgumentArray()[3]).getFieldName().equals("event_id");
        }));
    }

    @Test
    public void shouldLogResponseSuccess() {

        hmrcResource.getHmrcData(new IncomeDataRequest(FIRST_NAME, LAST_NAME, NINO, DATE_OF_BIRTH, FROM_DATE, TO_DATE, ALIAS_SURNAMES));

        verify(mockAppender).doAppend(argThat(argument -> {
            LoggingEvent loggingEvent = (LoggingEvent) argument;

            return loggingEvent.getFormattedMessage().equals("Income summary successfully retrieved from HMRC") &&
                            ((ObjectAppendingMarker) loggingEvent.getArgumentArray()[0]).getFieldName().equals("event_id") &&
                            ((ObjectAppendingMarker) loggingEvent.getArgumentArray()[1]).getFieldName().equals("request_duration_ms");
        }));
    }
}