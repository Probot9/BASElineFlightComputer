package com.platypii.baseline.events;

/**
 * Indicates that sign in or sign out status has changed
 */
public class AuthEvent {

    public static final AuthEvent SIGNED_OUT = new AuthEvent("SignedOut");
    public static final AuthEvent SIGNING_IN = new AuthEvent("SigningIn");
    public static final AuthEvent SIGNED_IN = new AuthEvent("SignedIn");

    public final String state;
    private AuthEvent(String state) {
        this.state = state;
    }

    public static AuthEvent fromString(String state) {
        if(SIGNED_OUT.state.equals(state)) {
            return SIGNED_OUT;
        } else if(SIGNING_IN.state.equals(state)) {
            return SIGNING_IN;
        } else if(SIGNED_IN.state.equals(state)) {
            return SIGNED_IN;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "AuthEvent(" + state + ")";
    }
}
