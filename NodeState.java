package LayeredBFS;

public enum NodeState {
    FINDPARENT,
    FINDCHILDREN,
    WAIT_FOR_ACK,
    RELAY_BROADCAST,
    RELAY_CONVERGECAST,
}
