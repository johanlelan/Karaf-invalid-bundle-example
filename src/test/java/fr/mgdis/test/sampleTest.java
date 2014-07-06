package fr.mgdis.test;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.timer.TimerEndpoint;
import org.apache.camel.test.spring.DisableJmx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * This class has to be a template for all the testing units. You must implement
 * two unit tests for all routes. One for nominal behavior One for nominal error
 * handler
 * 
 * @author Philippe-y
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
// Load camel-context-test.xml file for testing
@ContextConfiguration({ "/camel-context-test.xml" })
// Declare context as dirty. To remove it after all tests method
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@DisableJmx(false)
public class sampleTest {

    static final Logger logger = LoggerFactory
            .getLogger(sampleTest.class);

    @Inject
    @Named("testBroker")
    // spring injection of broker configuration
    // defined on broker-test.xml
    protected BrokerService broker;

    @Inject
    @Named("test-context")
    // spring injection of camel context for testing
    // defined on camel-context-test.xml
    protected CamelContext testContext;

    @Inject
    @Named("camel-context")
    // spring injection of production camel context
    // defined on META-INF/spring/camel-context.xml
    protected CamelContext context;

    @EndpointInject(uri = "mock://result", context = "camel-context")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct://start", context = "camel-context")
    protected ProducerTemplate template;

    @Value("${incoming.endpoint}")
    protected String in;

    @Value("${outgoing.endpoint}")
    protected String out;

    @Before
    public void setUp() throws Exception {
        // start the broker if it is not already started
        if (!broker.isStarted()) {
            broker.start(true);
        }
        // wait for all defined routes to be started
        broker.waitUntilStarted();

        // before all tests method, reset mock to ensure messages count.
        for (Endpoint endP : context.getEndpoints()) {
            if (endP instanceof MockEndpoint) {
                ((MockEndpoint) endP).reset();
            }
        }

        // in this unit test, we need to send a message in an specific
        // queue. We are creating a new route in test context that
        // sends messages into this mediation queue.
        testContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

            }
        });

        // To test the result, all the messages are finally destined
        // to a mock:result endPoint.
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct://start").inOut(in);
                from(out).to("mock://result");
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        // first of all, stop and shutdown all routes in test context
        for (Route route : testContext.getRoutes()) {
            testContext.stopRoute(route.getId());
            testContext.removeRoute(route.getId());
        }
        // reset all mocks, even if this is done on setUp()
        MockEndpoint.resetMocks(testContext);

        // For all activeMQ destination queues, make a
        // graceful shutdown with only 1sec timeout
        for (ActiveMQDestination dest : broker.getRegionBroker()
                .getDestinations()) {
            broker.getRegionBroker().removeDestination(
                    broker.getAdminConnectionContext(), dest, 1000);
        }

        // kill all active endPoints on production camel context
        for (Endpoint endP : context.getEndpoints()) {
            if (endP instanceof TimerEndpoint) {
                ((TimerEndpoint) endP).shutdown();
            }
        }
    }

    @Test
    public void testExample() throws Exception {
        // Arrange
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            private final transient Logger log = LoggerFactory.getLogger(this
                    .getClass());

            public boolean matches(Exchange exchange) {
                boolean isMatched = false;
                try {
                    // response body string load
                    String body = exchange.getIn().getBody(String.class);
                    isMatched = body.contains("XSLT");
                } catch (TypeConversionException e) {
                    log.error("When converting body", e);
                }
                return isMatched;
            }
        });

        // Act
        template.sendBody("<root/>");
        // Assert
        resultEndpoint.assertIsSatisfied();
    }
}
