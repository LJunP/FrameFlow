package com.frameflow.probe;

/** Checks whether dependencies required to receive traffic are available. */
public interface ReadinessChecker {

    boolean isReady();
}
