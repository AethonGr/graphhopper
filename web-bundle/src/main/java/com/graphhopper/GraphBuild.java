package com.graphhopper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.http.GraphHopperManaged;
import com.graphhopper.util.StopWatch;

import java.io.*;

public class GraphBuild {

    public static GraphHopperConfig getConfigFromFile(String filename) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        InputStream is = new BufferedInputStream(new FileInputStream(filename));
        return objectMapper.readValue(is, GraphHopperConfig.class);
    }

    public static void main(String[] args) throws Exception {
        StopWatch stopwatch = new StopWatch();
        stopwatch.start();
        GraphHopperConfig ghConfig = getConfigFromFile(args[0]);
        GraphHopperManaged ghm = new GraphHopperManaged(ghConfig);
        GraphHopper graphHopper = ghm.getGraphHopper();
        graphHopper.build();
        stopwatch.stop();
        System.out.println(stopwatch.getTimeString());
    }
}


