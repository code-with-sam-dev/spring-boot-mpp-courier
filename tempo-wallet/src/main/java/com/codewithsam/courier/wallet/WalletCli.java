package com.codewithsam.courier.wallet;

import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/**
 * Testnet wallet bootstrap. Prints addresses and balances, never a private key.
 *
 * <pre>
 *   new-payer .env        create the assistant's key, fund it from the faucet
 *   new-courier .env      create the courier's receiving address and MPP secret
 *   balance 0xADDRESS...  pathUSD balance of each address
 * </pre>
 */
public final class WalletCli {

    private static final TempoNetwork NETWORK = TempoNetwork.MODERATO;

    private WalletCli() {
    }

    public static void main(String[] args) throws Exception {
        TempoRpc rpc = new TempoRpc(NETWORK.rpcUrl());
        switch (args.length == 0 ? "" : args[0]) {
            case "new-payer" -> newPayer(rpc, new EnvFile(Path.of(args[1])));
            case "new-courier" -> newCourier(new EnvFile(Path.of(args[1])));
            case "balance" -> {
                for (int i = 1; i < args.length; i++) {
                    printBalance(rpc, args[i]);
                }
            }
            default -> {
                System.err.println("usage: new-payer <env> | new-courier <env> | balance <addr>...");
                System.exit(2);
            }
        }
    }

    private static void newPayer(TempoRpc rpc, EnvFile env) throws Exception {
        if (env.get("PAYER_PRIVATE_KEY").isPresent()) {
            System.out.println("A payer key already exists; delete it from .env to replace it.");
        } else {
            Credentials payer = Credentials.create(Keys.createEcKeyPair());
            env.put("PAYER_PRIVATE_KEY", Numeric.toHexStringWithPrefixZeroPadded(
                    payer.getEcKeyPair().getPrivateKey(), 64));
        }
        String address = Credentials.create(env.get("PAYER_PRIVATE_KEY").orElseThrow())
                .getAddress();
        env.put("PAYER_ADDRESS", address);
        System.out.println("payer address  " + address);
        System.out.println("faucet         " + rpc.fundAddress(address).size()
                + " funding transactions sent");
        for (int poll = 0; poll < 20 && balance(rpc, address).signum() == 0; poll++) {
            Thread.sleep(1000);
        }
        printBalance(rpc, address);
    }

    private static void newCourier(EnvFile env) throws Exception {
        if (env.get("COURIER_RECIPIENT_ADDRESS").isEmpty()) {
            // Only the address is kept: the courier server never needs the key.
            String address = Credentials.create(Keys.createEcKeyPair()).getAddress();
            env.put("COURIER_RECIPIENT_ADDRESS", address);
        }
        if (env.get("MPP_SECRET_KEY").isEmpty()) {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            env.put("MPP_SECRET_KEY", HexFormat.of().formatHex(secret));
        }
        System.out.println("courier address "
                + env.get("COURIER_RECIPIENT_ADDRESS").orElseThrow());
    }

    private static BigInteger balance(TempoRpc rpc, String address) {
        return rpc.balanceOf(PathUsd.ADDRESS, address);
    }

    private static void printBalance(TempoRpc rpc, String address) {
        System.out.println(address + "  "
                + PathUsd.fromBaseUnits(balance(rpc, address)).toPlainString() + " pathUSD");
    }
}
