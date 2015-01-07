package name.abuchen.portfolio.money;

import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CurrencyConverterImpl implements CurrencyConverter
{
    private final ExchangeRateProviderFactory factory;
    private final String termCurrency;

    private final Map<String, ExchangeRateTimeSeries> cache = new HashMap<String, ExchangeRateTimeSeries>();

    public CurrencyConverterImpl(ExchangeRateProviderFactory factory, String termCurrency)
    {
        this.factory = factory;
        this.termCurrency = termCurrency;
    }

    @Override
    public String getTermCurrency()
    {
        return termCurrency;
    }

    @Override
    public Money convert(Date date, Money amount)
    {
        if (termCurrency.equals(amount.getCurrencyCode()))
            return amount;

        if (amount.isZero())
            return Money.of(termCurrency, 0);

        ExchangeRate rate = getRate(date, amount.getCurrencyCode());

        return Money.of(termCurrency,
                        Math.round((amount.getAmount() * rate.getValue()) / Values.ExchangeRate.divider()));
    }

    @Override
    public ExchangeRate getRate(Date date, String currencyCode)
    {
        if (termCurrency.equals(currencyCode))
            return new ExchangeRate(date, Values.ExchangeRate.factor());

        ExchangeRateTimeSeries series = cache.computeIfAbsent(currencyCode, code -> lookupSeries(code));

        Optional<ExchangeRate> rate = series.lookupRate(date);
        if (!rate.isPresent())
            throw new MonetaryException(MessageFormat.format("No rate available to convert from {0} to {1}",
                            currencyCode, termCurrency));

        return rate.get();
    }

    private ExchangeRateTimeSeries lookupSeries(String currencyCode)
    {
        ExchangeRateTimeSeries series = factory.getTimeSeries(currencyCode, termCurrency);
        if (series == null)
            throw new MonetaryException(MessageFormat.format("Unable to convert from {0} to {1}", currencyCode,
                            termCurrency));

        return series;
    }

    @Override
    public CurrencyConverter with(String currencyCode)
    {
        if (currencyCode.equals(termCurrency))
            return this;

        return new CurrencyConverterImpl(factory, currencyCode);
    }
}
