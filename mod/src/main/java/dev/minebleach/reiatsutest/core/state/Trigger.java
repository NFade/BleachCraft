package dev.minebleach.reiatsutest.core.state;

/** Why the state changed. REQUEST covers voice and key (the machine does not tell them apart). */
public enum Trigger {
	REQUEST, BANKAI_CAP, SHIKAI_IDLE, REIATSU_ZERO, HAND_LOST, ITEM_DROPPED, DEATH, LOGOUT, DIMENSION_CHANGE, DEV_COMMAND
}
