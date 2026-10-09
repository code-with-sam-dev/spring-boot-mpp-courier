package com.codewithsam.courier.wallet;

import java.math.BigInteger;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

/** ABI call data for the two TIP-20 functions this project needs. */
public final class TempoCalls {

    private TempoCalls() {
    }

    /** transferWithMemo(address, uint256, bytes32), selector 0x95777d59. */
    public static String transferWithMemo(String to, BigInteger amount, String memo) {
        return FunctionEncoder.encode(new Function("transferWithMemo", List.of(
                new Address(to),
                new Uint256(amount),
                new Bytes32(Numeric.hexStringToByteArray(memo))), List.of()));
    }

    public static String balanceOf(String owner) {
        return FunctionEncoder.encode(
                new Function("balanceOf", List.of(new Address(owner)), List.of()));
    }
}
