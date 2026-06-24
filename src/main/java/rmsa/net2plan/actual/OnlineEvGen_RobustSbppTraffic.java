package rmsa.net2plan.actual;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.simulation.IEventGenerator;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.RandomUtils;
import com.net2plan.utils.Triple;

public final class OnlineEvGen_RobustSbppTraffic extends IEventGenerator {
    private final InputParameter trafficLayerId = new InputParameter(
            "trafficLayerId",
            -1L,
            "Layer containing traffic demands (-1 means default layer)");
    private final InputParameter randomSeed = new InputParameter(
            "randomSeed",
            1L,
            "Seed for the random generator (-1 means random)");
    private final InputParameter arrivalPattern = new InputParameter(
            "arrivalPattern",
            "#select# random-exponential-arrivals-and-duration random-exponential-arrivals-deterministic-duration deterministic",
            "Connection arrival and duration pattern");
    private final InputParameter averageHoldingTimeHours = new InputParameter(
            "averageHoldingTimeHours",
            1.0D,
            "Average connection holding time in hours",
            0.0D,
            false,
            Double.MAX_VALUE,
            true);
    private final InputParameter minConnectionSizeGbps = new InputParameter(
            "minConnectionSizeGbps",
            50.0D,
            "Minimum random connection bitrate in Gbps",
            0.0D,
            false,
            Double.MAX_VALUE,
            true);
    private final InputParameter maxConnectionSizeGbps = new InputParameter(
            "maxConnectionSizeGbps",
            700.0D,
            "Maximum random connection bitrate in Gbps",
            0.0D,
            false,
            Double.MAX_VALUE,
            true);
    private final InputParameter connectionSizeMode = new InputParameter(
            "connectionSizeMode",
            "#select# continuous-uniform discrete-50-step",
            "How to generate random connection bitrates between the configured minimum and maximum");

    private Random rng;
    private NetworkLayer trafficLayer;
    private boolean iatDeterministic;
    private boolean iatExponential;
    private boolean durationDeterministic;
    private boolean durationExponential;

    @Override
    public String getDescription() {
        return "Generates dynamic SBPP-NLIA connection requests/releases with random bitrates between 50 and 700 Gbps by default.";
    }

    @Override
    public List<Triple<String, String, String>> getParameters() {
        return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
    }

    @Override
    public void initialize(
            NetPlan initialNetPlan,
            Map<String, String> algorithmParameters,
            Map<String, String> simulationParameters,
            Map<String, String> net2planParameters) {
        InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);
        validateParameters();

        trafficLayer = trafficLayerId.getLong().longValue() == -1L
                ? initialNetPlan.getNetworkLayerDefault()
                : initialNetPlan.getNetworkLayerFromId(trafficLayerId.getLong().longValue());
        if (trafficLayer == null) {
            throw new Net2PlanException("Unknown traffic layer id");
        }
        if (initialNetPlan.getNumberOfDemands(trafficLayer) == 0) {
            throw new Net2PlanException("No demands were defined in the input design");
        }

        if (randomSeed.getLong().longValue() == -1L) {
            randomSeed.initialize(RandomUtils.random(0L, Long.MAX_VALUE - 1L));
        }
        rng = new Random(randomSeed.getLong().longValue());

        String pattern = arrivalPattern.getString();
        iatDeterministic = pattern.equalsIgnoreCase("deterministic");
        iatExponential = pattern.equalsIgnoreCase("random-exponential-arrivals-deterministic-duration")
                || pattern.equalsIgnoreCase("random-exponential-arrivals-and-duration");
        durationDeterministic = pattern.equalsIgnoreCase("deterministic")
                || pattern.equalsIgnoreCase("random-exponential-arrivals-deterministic-duration");
        durationExponential = pattern.equalsIgnoreCase("random-exponential-arrivals-and-duration");

        for (Demand demand : initialNetPlan.getDemands(trafficLayer)) {
            double firstConnectionSizeGbps = expectedConnectionSizeGbps();
            scheduleEvent(new SimEvent(
                    nextInterArrivalSeconds(demand, firstConnectionSizeGbps),
                    SimEvent.DestinationModule.EVENT_GENERATOR,
                    -1,
                    new GenerateConnectionRequest(demand)));
        }
    }

    @Override
    public void processEvent(NetPlan currentNetPlan, SimEvent event) {
        Object eventObject = event.getEventObject();
        if (eventObject instanceof GenerateConnectionRequest) {
            processConnectionRequest(event.getEventTime(), (GenerateConnectionRequest) eventObject);
        } else if (eventObject instanceof GenerateConnectionRelease) {
            processConnectionRelease(event.getEventTime(), (GenerateConnectionRelease) eventObject);
        }
    }

    private void processConnectionRequest(double simTime, GenerateConnectionRequest request) {
        Demand demand = request.demand;
        double connectionSizeGbps = generateRandomConnectionSizeGbps();
        double holdingTimeSeconds = nextHoldingTimeSeconds();
        double interArrivalSeconds = nextInterArrivalSeconds(demand, connectionSizeGbps);

        SimEvent.RouteAdd routeAdd = new SimEvent.RouteAdd(
                demand,
                null,
                connectionSizeGbps,
                connectionSizeGbps);
        scheduleEvent(new SimEvent(simTime, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, routeAdd));
        scheduleEvent(new SimEvent(
                simTime + holdingTimeSeconds,
                SimEvent.DestinationModule.EVENT_GENERATOR,
                -1,
                new GenerateConnectionRelease(routeAdd)));
        scheduleEvent(new SimEvent(
                simTime + interArrivalSeconds,
                SimEvent.DestinationModule.EVENT_GENERATOR,
                -1,
                new GenerateConnectionRequest(demand)));
    }

    private void processConnectionRelease(double simTime, GenerateConnectionRelease release) {
        if (release.routeAdd.routeAddedToFillByProcessor != null) {
            scheduleEvent(new SimEvent(
                    simTime,
                    SimEvent.DestinationModule.EVENT_PROCESSOR,
                    -1,
                    new SimEvent.RouteRemove(release.routeAdd.routeAddedToFillByProcessor)));
        }
    }

    private double nextInterArrivalSeconds(Demand demand, double connectionSizeGbps) {
        double offeredTrafficGbps = demand.getOfferedTraffic();
        if (offeredTrafficGbps <= 0.0) {
            throw new Net2PlanException("Demand " + demand.getId() + " has non-positive offered traffic");
        }
        double meanHoldingSeconds = averageHoldingTimeHours.getDouble() * 3600.0D;
        double averageInterArrivalSeconds = connectionSizeGbps * meanHoldingSeconds / offeredTrafficGbps;
        if (iatDeterministic) {
            return averageInterArrivalSeconds;
        }
        if (iatExponential) {
            return exponential(averageInterArrivalSeconds);
        }
        throw new Net2PlanException("Unsupported arrival pattern: " + arrivalPattern.getString());
    }

    private double nextHoldingTimeSeconds() {
        double meanHoldingSeconds = averageHoldingTimeHours.getDouble() * 3600.0D;
        if (durationDeterministic) {
            return meanHoldingSeconds;
        }
        if (durationExponential) {
            return exponential(meanHoldingSeconds);
        }
        throw new Net2PlanException("Unsupported duration pattern: " + arrivalPattern.getString());
    }

    private double generateRandomConnectionSizeGbps() {
        double min = minConnectionSizeGbps.getDouble();
        double max = maxConnectionSizeGbps.getDouble();
        if ("discrete-50-step".equalsIgnoreCase(connectionSizeMode.getString())) {
            int steps = (int) Math.floor((max - min) / 50.0D);
            return min + 50.0D * rng.nextInt(steps + 1);
        }
        return min + rng.nextDouble() * (max - min);
    }

    private double expectedConnectionSizeGbps() {
        return (minConnectionSizeGbps.getDouble() + maxConnectionSizeGbps.getDouble()) / 2.0D;
    }

    private double exponential(double mean) {
        double u = Math.max(1.0e-12, 1.0D - rng.nextDouble());
        return -mean * Math.log(u);
    }

    private void validateParameters() {
        if (minConnectionSizeGbps.getDouble() <= 0.0 || maxConnectionSizeGbps.getDouble() <= 0.0) {
            throw new Net2PlanException("Connection bitrates must be positive");
        }
        if (minConnectionSizeGbps.getDouble() > maxConnectionSizeGbps.getDouble()) {
            throw new Net2PlanException("Minimum connection bitrate cannot exceed maximum connection bitrate");
        }
        if ("discrete-50-step".equalsIgnoreCase(connectionSizeMode.getString())
                && maxConnectionSizeGbps.getDouble() < minConnectionSizeGbps.getDouble() + 50.0D) {
            throw new Net2PlanException("Discrete mode requires at least one 50 Gbps step");
        }
    }

    private static final class GenerateConnectionRequest {
        private final Demand demand;

        private GenerateConnectionRequest(Demand demand) {
            this.demand = demand;
        }

        @Override
        public String toString() {
            return "Generate robust SBPP connection request for demand " + demand.getId();
        }
    }

    private static final class GenerateConnectionRelease {
        private final SimEvent.RouteAdd routeAdd;

        private GenerateConnectionRelease(SimEvent.RouteAdd routeAdd) {
            this.routeAdd = routeAdd;
        }

        @Override
        public String toString() {
            return "Generate robust SBPP connection release";
        }
    }
}
