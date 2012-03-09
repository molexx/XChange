/**
 * Copyright (C) 2012 Xeiam LLC http://xeiam.com
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xeiam.xchange.mtgox.v1.service.trade;

import com.xeiam.xchange.Constants;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.service.BaseExchangeService;
import com.xeiam.xchange.service.trade.*;
import com.xeiam.xchange.utils.Assert;
import com.xeiam.xchange.utils.CryptoUtils;
import com.xeiam.xchange.utils.HttpTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MtGoxTradeService extends BaseExchangeService implements TradeService {

  private final Logger log = LoggerFactory.getLogger(MtGoxTradeService.class);

  /**
   * Configured from the super class reading of the exchange specification
   */
  private final String apiBaseURI = String.format("%s/api/%s/", apiURI, apiVersion);

  /**
   * Initialize common properties from the exchange specification
   * 
   * @param exchangeSpecification The exchange specification with the configuration parameters
   */
  public MtGoxTradeService(ExchangeSpecification exchangeSpecification) {
    super(exchangeSpecification);
  }

  @Override
  public AccountInfo getAccountInfo() {

    // verify
    Assert.notNull(apiKey, "apiKey cannot be null");
    Assert.notNull(apiSecret, "apiSecret cannot be null");
    Assert.notNull(apiURI, "apiURI cannot be null");
    Assert.notNull(apiVersion, "apiVersion cannot be null");

    // Build request
    String url = apiBaseURI + "/generic/private/info?raw";
    String postBody = "nonce=" + CryptoUtils.getNumericalNonce();

    // Request data
    MtGoxAccountInfo mtGoxAccountInfo = httpTemplate.postForJsonObject(url, MtGoxAccountInfo.class, postBody, mapper, getMtGoxAuthenticationHeaderKeyValues(postBody));

    // Adapt to XChange DTOs
    AccountInfo accountInfo = new AccountInfo();
    accountInfo.setUsername(mtGoxAccountInfo.getLogin());

    List<Wallet> wallets = new ArrayList<Wallet>();
    Wallet usdWallet = new Wallet();
    usdWallet.setCurrency(Constants.USD);
    usdWallet.setAmount_int(mtGoxAccountInfo.getWallets().getUSD().getBalance().getValue_int());
    wallets.add(usdWallet);
    Wallet btcWallet = new Wallet();
    btcWallet.setCurrency(Constants.BTC);
    btcWallet.setAmount_int(mtGoxAccountInfo.getWallets().getBTC().getBalance().getValue_int());
    wallets.add(btcWallet);
    accountInfo.setWallets(wallets);

    return accountInfo;

  }

  @Override
  public OpenOrders getOpenOrders() {

    // verify
    Assert.notNull(apiKey, "apiKey cannot be null");
    Assert.notNull(apiSecret, "apiSecret cannot be null");
    Assert.notNull(apiURI, "apiURI cannot be null");
    Assert.notNull(apiVersion, "apiVersion cannot be null");

    // Build request
    String url = apiBaseURI + "/generic/private/orders?raw";
    String postBody = "nonce=" + CryptoUtils.getNumericalNonce();

    // Request data
    MtGoxOpenOrder[] mtGoxOpenOrder = httpTemplate.postForJsonObject(url, MtGoxOpenOrder[].class, postBody, mapper, getMtGoxAuthenticationHeaderKeyValues(postBody));

    // Adapt to XChange DTOs
    List<LimitOrder> openOrdersList = new ArrayList<LimitOrder>();
    for (int i = 0; i < mtGoxOpenOrder.length; i++) {
      LimitOrder openOrder = new LimitOrder();
      openOrder.setType(mtGoxOpenOrder[i].getType().equalsIgnoreCase("bid") ? Constants.BID : Constants.ASK);
      openOrder.setAmount_int(mtGoxOpenOrder[i].getAmount().getValue_int());
      openOrder.setAmountCurrency(mtGoxOpenOrder[i].getAmount().getCurrency());

      openOrder.setPrice_int(mtGoxOpenOrder[i].getPrice().getValue_int());
      openOrder.setPriceCurrency(mtGoxOpenOrder[i].getPrice().getCurrency());

      openOrdersList.add(openOrder);
    }
    OpenOrders openOrders = new OpenOrders();
    openOrders.setOpenOrders(openOrdersList);

    return openOrders;

  }

  @Override
  public boolean placeMarketOrder(MarketOrder marketOrder) {

    // verify
    Assert.notNull(apiKey, "apiKey cannot be null");
    Assert.notNull(apiSecret, "apiSecret cannot be null");
    Assert.notNull(apiURI, "apiURI cannot be null");
    Assert.notNull(apiVersion, "apiVersion cannot be null");

    Assert.notNull(marketOrder.getAmountCurrency(), "getAmountCurrency() cannot be null");
    Assert.notNull(marketOrder.getPriceCurrency(), "getPriceCurrency() cannot be null");
    Assert.notNull(marketOrder.getType(), "getType() cannot be null");
    Assert.notNull(marketOrder.getAmount_int(), "getAmount_int() cannot be null");

    // Build request
    String symbol = marketOrder.getAmountCurrency() + marketOrder.getPriceCurrency();
    String type = marketOrder.getType().equals(Constants.BID) ? "bid" : "ask";
    String amount = "" + marketOrder.getAmount_int();
    String url = apiBaseURI + symbol + "/private/order/add";

    String postBody = "nonce=" + CryptoUtils.getNumericalNonce() + "&type=" + type + "&amount_int=" + amount;

    // Request data
    MtGoxGenericResponse mtGoxSuccess = httpTemplate.postForJsonObject(url, MtGoxGenericResponse.class, postBody, mapper, getMtGoxAuthenticationHeaderKeyValues(postBody));

    return mtGoxSuccess.getResult().equals("success") ? true : false;
  }

  @Override
  public boolean placeLimitOrder(LimitOrder limitOrder) {

    // verify
    Assert.notNull(apiKey, "apiKey cannot be null");
    Assert.notNull(apiSecret, "apiSecret cannot be null");
    Assert.notNull(apiURI, "apiURI cannot be null");
    Assert.notNull(apiVersion, "apiVersion cannot be null");

    Assert.notNull(limitOrder.getAmountCurrency(), "getAmountCurrency() cannot be null");
    Assert.notNull(limitOrder.getPriceCurrency(), "getPriceCurrency() cannot be null");
    Assert.notNull(limitOrder.getType(), "getType() cannot be null");
    Assert.notNull(limitOrder.getAmount_int(), "getAmount_int() cannot be null");
    Assert.notNull(limitOrder.getPrice_int(), "getPrice_int() cannot be null");

    // Build request
    String symbol = limitOrder.getAmountCurrency() + limitOrder.getPriceCurrency();
    String type = limitOrder.getType().equals(Constants.BID) ? "bid" : "ask";
    String amount = "" + limitOrder.getAmount_int();
    String price_int = "" + limitOrder.getPrice_int();
    String url = apiBaseURI + symbol + "/private/order/add";

    String postBody = "nonce=" + CryptoUtils.getNumericalNonce() + "&type=" + type + "&amount_int=" + amount + "&price_int=" + price_int;

    // Request data
    MtGoxGenericResponse mtGoxSuccess = httpTemplate.postForJsonObject(url, MtGoxGenericResponse.class, postBody, mapper, getMtGoxAuthenticationHeaderKeyValues(postBody));

    return mtGoxSuccess.getResult().equals("success") ? true : false;
  }

  /**
   * Generates necessary authentication header values for MtGox
   * 
   * @param postBody
   * @return
   */
  private Map<String, String> getMtGoxAuthenticationHeaderKeyValues(String postBody) {

    try {

      Map<String, String> headerKeyValues = new HashMap<String, String>();

      headerKeyValues.put("Rest-Key", URLEncoder.encode(apiKey, HttpTemplate.CHARSET_UTF_8));
      headerKeyValues.put("Rest-Sign", CryptoUtils.computeSignature("HmacSHA512", postBody, apiSecret));
      return headerKeyValues;

    } catch (GeneralSecurityException e) {
      throw new ExchangeException("Problem generating secure HTTP request (General Security)", e);
    } catch (UnsupportedEncodingException e) {
      throw new ExchangeException("Problem generating secure HTTP request  (Unsupported Encoding)", e);
    }
  }
}