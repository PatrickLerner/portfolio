package name.abuchen.portfolio.datatransfer.pdf.consorsbank;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import name.abuchen.portfolio.datatransfer.Extractor.BuySellEntryItem;
import name.abuchen.portfolio.datatransfer.Extractor.Item;
import name.abuchen.portfolio.datatransfer.Extractor.SecurityItem;
import name.abuchen.portfolio.datatransfer.Extractor.TransactionItem;
import name.abuchen.portfolio.datatransfer.ImportAction.Status;
import name.abuchen.portfolio.datatransfer.ImportAction.Status.Code;
import name.abuchen.portfolio.datatransfer.actions.AssertImportActions;
import name.abuchen.portfolio.datatransfer.actions.CheckCurrenciesAction;
import name.abuchen.portfolio.datatransfer.pdf.ConsorsbankPDFExtractor;
import name.abuchen.portfolio.datatransfer.pdf.PDFInputFile;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.model.Transaction.Unit.Type;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Quote;
import name.abuchen.portfolio.money.Values;

@SuppressWarnings("nls")
public class ConsorsbankPDFExtractorTest
{

    private Security assertSecurity(List<Item> results, boolean mustHaveIsin)
    {
        Optional<Item> item = results.stream().filter(i -> i instanceof SecurityItem).findFirst();
        assertThat(item.isPresent(), is(true));
        Security security = ((SecurityItem) item.get()).getSecurity();
        if (mustHaveIsin)
            assertThat(security.getIsin(), is("LU0392494562"));
        assertThat(security.getWkn(), is("ETF110"));
        assertThat(security.getName(), is("COMS.-MSCI WORL.T.U.ETF I"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));

        return security;
    }

    @Test
    public void testErtragsgutschrift() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check security
        Security security = assertSecurity(results, false);

        // check transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof TransactionItem).findFirst();
        assertDividendTransaction(security, item);

        // check tax
        AccountTransaction t = (AccountTransaction) item.get().getSubject();
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 111_00L + 6_10L)));
    }

    private void assertDividendTransaction(Security security, Optional<Item> item)
    {
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(AccountTransaction.class));
        AccountTransaction transaction = (AccountTransaction) item.get().getSubject();
        assertThat(transaction.getType(), is(AccountTransaction.Type.DIVIDENDS));
        assertThat(transaction.getSecurity(), is(security));
        assertThat(transaction.getDateTime(), is(LocalDateTime.parse("2015-05-08T00:00")));
        assertThat(transaction.getMonetaryAmount(), is(Money.of("EUR", 326_90L)));
        assertThat(transaction.getShares(), is(Values.Share.factorize(1370)));
    }

    @Test
    public void testErtragsgutschrift2() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift2.txt"), errors);

        assertThat(errors, empty());

        // since taxes are zero, no tax transaction must be created
        assertThat(results.size(), is(2));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        Optional<Item> item = results.stream().filter(i -> i instanceof TransactionItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(1444))));
    }

    @Test
    public void testErtragsgutschrift3() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift3.txt"), errors);

        assertThat(errors, empty());

        // since taxes are zero, no tax transaction must be created
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findAny().get().getSecurity();
        assertThat(security.getWkn(), is("850866"));
        assertThat(security.getName(), is("DEERE & CO."));

        // check dividend transaction
        AccountTransaction t = (AccountTransaction) results.stream().filter(i -> i instanceof TransactionItem).filter(
                        i -> ((AccountTransaction) i.getSubject()).getType() == AccountTransaction.Type.DIVIDENDS)
                        .findAny().get().getSubject();
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2015-11-02T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(300)));
        assertThat(t.getMonetaryAmount(), is(Money.of("EUR", 121_36)));
        assertThat(t.getUnit(Unit.Type.GROSS_VALUE).get().getForex(), is(Money.of("USD", 180_00)));

        // check tax
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 16_30 + 89 + 24_45)));
    }

    @Test
    public void testErtragsgutschrift4() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift4.txt"), errors);

        assertThat(errors, empty());

        // since taxes are zero, no tax transaction must be created
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findFirst().get().getSecurity();
        assertThat(security.getWkn(), is("854242"));
        assertThat(security.getName(), is("WESTPAC BANKING CORP."));

        // check dividend transaction
        AccountTransaction t = (AccountTransaction) results.stream().filter(i -> i instanceof TransactionItem).filter(
                        i -> ((AccountTransaction) i.getSubject()).getType() == AccountTransaction.Type.DIVIDENDS)
                        .findFirst().get().getSubject();
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2015-07-02T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(1.0002)));
        assertThat(t.getMonetaryAmount(), is(Money.of("EUR", 46)));
        assertThat(t.getUnit(Unit.Type.GROSS_VALUE).get().getForex(), is(Money.of("AUD", 93)));

        // check tax
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 16)));
    }

    @Test
    public void testErtragsgutschrift5() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift5.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findFirst().get().getSecurity();
        assertThat(security.getWkn(), is("885823"));
        assertThat(security.getName(), is("GILEAD SCIENCES INC."));

        // check dividend transaction
        AccountTransaction t = (AccountTransaction) results.stream().filter(i -> i instanceof TransactionItem).filter(
                        i -> ((AccountTransaction) i.getSubject()).getType() == AccountTransaction.Type.DIVIDENDS)
                        .findFirst().get().getSubject();
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2015-06-29T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(0.27072)));
        assertThat(t.getMonetaryAmount(), is(Money.of("EUR", 8)));
        assertThat(t.getUnit(Unit.Type.GROSS_VALUE).get().getForex(), is(Money.of("USD", 12)));

        // check tax
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 1 + 2)));
    }

    @Test
    public void testErtragsgutschrift8() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift8.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findFirst().get().getSecurity();
        assertThat(security.getWkn(), is("891106"));
        assertThat(security.getName(), is("ROCHE HOLDING AG"));

        // check dividend transaction
        AccountTransaction t = (AccountTransaction) results.stream().filter(i -> i instanceof TransactionItem).filter(
                        i -> ((AccountTransaction) i.getSubject()).getType() == AccountTransaction.Type.DIVIDENDS)
                        .findFirst().get().getSubject();
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2014-04-22T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(80)));
        assertThat(t.getMonetaryAmount(), is(Money.of("EUR", 33_51)));
        assertThat(t.getGrossValue(), is(Money.of("EUR", 64_08)));

        // check tax
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 30_57)));

        checkCurrency(CurrencyUnit.EUR, t);
    }

    @Test
    public void testErtragsgutschrift9() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift9.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findFirst().get().getSecurity();
        assertThat(security.getWkn(), is("A1409D"));
        assertThat(security.getName(), is("Welltower Inc."));

        // check dividend transaction
        AccountTransaction t = (AccountTransaction) results.stream().filter(i -> i instanceof TransactionItem).filter(
                        i -> ((AccountTransaction) i.getSubject()).getType() == AccountTransaction.Type.DIVIDENDS)
                        .findFirst().get().getSubject();
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2016-05-20T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(50)));
        assertThat(t.getMonetaryAmount(), is(Money.of("EUR", 32_51)));
        assertThat(t.getGrossValue(), is(Money.of("EUR", 38_25)));

        // check tax
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 5_74)));

        checkCurrency(CurrencyUnit.EUR, t);
    }

    @Test
    public void testErtragsgutschrift8WithExistingSecurity() throws IOException
    {
        Client client = new Client();
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(client);

        Security existingSecurity = new Security("ROCHE HOLDING AG", CurrencyUnit.EUR);
        existingSecurity.setWkn("891106");
        client.addSecurity(existingSecurity);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift8.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));

        // check dividend transaction
        AccountTransaction t = (AccountTransaction) results.stream().filter(i -> i instanceof TransactionItem).filter(
                        i -> ((AccountTransaction) i.getSubject()).getType() == AccountTransaction.Type.DIVIDENDS)
                        .findFirst().get().getSubject();
        assertThat(t.getSecurity(), is(existingSecurity));
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2014-04-22T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(80)));
        assertThat(t.getMonetaryAmount(), is(Money.of("EUR", 33_51)));
        assertThat(t.getGrossValue(), is(Money.of("EUR", 64_08)));

        // check tax
        assertThat(t.getUnitSum(Type.TAX), is(Money.of("EUR", 30_57)));

        checkCurrency(CurrencyUnit.EUR, t);
    }

    @Test
    public void testErtragsgutschrift10() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());
        List<Exception> errors = new ArrayList<Exception>();

        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift10.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        AccountTransaction t = results.stream() //
                        .filter(i -> i instanceof TransactionItem)
                        .map(i -> (AccountTransaction) ((TransactionItem) i).getSubject()) //
                        .findAny().get();

        assertThat(t.getSecurity().getName(), is("OMNICOM GROUP INC. Registered Shares DL -,15"));
        assertThat(t.getSecurity().getIsin(), is("US6819191064"));
        assertThat(t.getSecurity().getWkn(), is("871706"));
        assertThat(t.getSecurity().getCurrencyCode(), is(CurrencyUnit.USD));

        assertThat(t.getDateTime(), is(LocalDateTime.parse("2018-01-09T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(25)));
        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(9.34))));

        Unit grossValue = t.getUnit(Unit.Type.GROSS_VALUE).get();
        assertThat(grossValue.getAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(10.66))));
        assertThat(grossValue.getForex(), is(Money.of(CurrencyUnit.USD, Values.Amount.factorize(12.75))));

        assertThat(t.getGrossValue(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(12.54))));

        assertThat(t.getUnitSum(Unit.Type.TAX),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(0.06 + 1.26 + 1.88))));
    }

    @Test
    public void testErtragsgutschrift10WithExistingSecurityInTransactionCurrency() throws IOException
    {
        Client client = new Client();

        Security security = new Security("Omnicom", CurrencyUnit.EUR);
        security.setIsin("US6819191064");
        client.addSecurity(security);

        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(client);
        List<Exception> errors = new ArrayList<Exception>();

        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift10.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));

        AccountTransaction t = results.stream() //
                        .filter(i -> i instanceof TransactionItem)
                        .map(i -> (AccountTransaction) ((TransactionItem) i).getSubject()) //
                        .findAny().get();

        assertThat(t.getSecurity(), is(security));

        assertThat(t.getDateTime(), is(LocalDateTime.parse("2018-01-09T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(25)));
        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(9.34))));

        assertThat(t.getUnit(Unit.Type.GROSS_VALUE).isPresent(), is(false));

        assertThat(t.getGrossValue(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(12.54))));

        assertThat(t.getUnitSum(Unit.Type.TAX),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(0.06 + 1.26 + 1.88))));
    }

    @Test
    public void testErtragsgutschrift11() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());
        List<Exception> errors = new ArrayList<Exception>();

        List<Item> results = extractor
                        .extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankErtragsgutschrift11.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        AccountTransaction t = results.stream() //
                        .filter(i -> i instanceof TransactionItem)
                        .map(i -> (AccountTransaction) ((TransactionItem) i).getSubject()) //
                        .findAny().get();

        assertThat(t.getSecurity().getName(), is("ComStage - MDAX UCITS ETF Inhaber-Anteile I o.N."));
        assertThat(t.getSecurity().getIsin(), is("LU1033693638"));
        assertThat(t.getSecurity().getWkn(), is("ETF007"));
        assertThat(t.getSecurity().getCurrencyCode(), is(CurrencyUnit.EUR));

        assertThat(t.getDateTime(), is(LocalDateTime.parse("2018-08-23T00:00")));
        assertThat(t.getShares(), is(Values.Share.factorize(43)));
        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(16.36))));

        assertThat(t.getUnitSum(Unit.Type.TAX), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(3.51 + 0.19))));
    }

    @Test
    public void testBezug1() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankBezug1.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findFirst().get().getSecurity();
        assertThat(security.getIsin(), is("DE000A0V9L94"));
        assertThat(security.getWkn(), is("A0V9L9"));
        assertThat(security.getName(), is("EYEMAXX R.EST.AG"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof BuySellEntryItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(BuySellEntry.class));
        BuySellEntry entry = (BuySellEntry) item.get().getSubject();

        assertThat(entry.getPortfolioTransaction().getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(entry.getAccountTransaction().getType(), is(AccountTransaction.Type.BUY));

        assertThat(entry.getPortfolioTransaction().getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 399_96L)));
        assertThat(entry.getPortfolioTransaction().getDateTime(), is(LocalDateTime.parse("2016-05-10T00:00")));
        assertThat(entry.getPortfolioTransaction().getShares(), is(66_000000L));
        assertThat(entry.getPortfolioTransaction().getUnitSum(Unit.Type.FEE), is(Money.of(CurrencyUnit.EUR, 3_96L)));
    }

    @Test
    public void testWertpapierVerkauf1() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankVerkauf1.txt"),
                        errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));

        // check security
        Security security = results.stream().filter(i -> i instanceof SecurityItem).findFirst().get().getSecurity();
        assertThat(security.getWkn(), is("A0DPR2"));
        assertThat(security.getName(), is("VOLKSWAGEN AG VZ ADR1/5"));

        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check buy sell transaction
        Item item = results.get(0);
        BuySellEntry entry = (BuySellEntry) item.getSubject();
        PortfolioTransaction t = entry.getPortfolioTransaction();
        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 5794_56L)));
        assertThat(t.getUnitSum(Type.FEE), is(Money.of(CurrencyUnit.EUR, 26_65L)));
        assertThat(t.getUnitSum(Type.TAX), is(Money.of(CurrencyUnit.EUR, 226_79L)));
        assertThat(t.getDateTime(), is(LocalDateTime.of(2015, 2, 18, 12, 10, 30)));
        assertThat(t.getShares(), is(Values.Share.factorize(140)));
        assertThat(t.getGrossPricePerShare(), is(Quote.of(CurrencyUnit.EUR, Values.Quote.factorize(43.2))));
    }

    @Test
    public void testWertpapierKauf1() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankKauf1.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check security
        assertSecurity(results, true);

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof BuySellEntryItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(BuySellEntry.class));
        BuySellEntry entry = (BuySellEntry) item.get().getSubject();

        assertThat(entry.getPortfolioTransaction().getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(entry.getAccountTransaction().getType(), is(AccountTransaction.Type.BUY));

        assertThat(entry.getPortfolioTransaction().getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 5000_00L)));
        assertThat(entry.getPortfolioTransaction().getDateTime(), is(LocalDateTime.of(2015, 1, 15, 8, 13, 35)));
        assertThat(entry.getPortfolioTransaction().getShares(), is(132_802120L));
        assertThat(entry.getPortfolioTransaction().getUnitSum(Unit.Type.FEE), is(Money.of(CurrencyUnit.EUR, 0L)));
    }

    @Test
    public void testWertpapierKauf2() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankKauf2.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check security
        Optional<Item> secItem = results.stream().filter(i -> i instanceof SecurityItem).findFirst();
        assertThat(secItem.isPresent(), is(true));
        Security security = ((SecurityItem) secItem.get()).getSecurity();
        assertThat(security.getIsin(), is("DE000A0L1NN5"));
        assertThat(security.getWkn(), is("A0L1NN"));
        assertThat(security.getName(), is("HELIAD EQ.PARTN.KGAA"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof BuySellEntryItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(BuySellEntry.class));
        BuySellEntry entry = (BuySellEntry) item.get().getSubject();

        assertThat(entry.getPortfolioTransaction().getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(entry.getAccountTransaction().getType(), is(AccountTransaction.Type.BUY));

        assertThat(entry.getPortfolioTransaction().getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 1387_85L)));
        assertThat(entry.getPortfolioTransaction().getDateTime(), is(LocalDateTime.of(2015, 9, 21, 12, 45, 38)));
        assertThat(entry.getPortfolioTransaction().getShares(), is(Values.Share.factorize(250)));
        assertThat(entry.getPortfolioTransaction().getUnitSum(Unit.Type.FEE), is(Money.of(CurrencyUnit.EUR, 17_85L)));
        assertThat(entry.getPortfolioTransaction().getGrossPricePerShare(),
                        is(Quote.of(CurrencyUnit.EUR, Values.Quote.factorize(5.48))));
    }

    @Test
    public void testWertpapierKauf3() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankKauf3.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check security
        Optional<Item> secItem = results.stream().filter(i -> i instanceof SecurityItem).findFirst();
        assertThat(secItem.isPresent(), is(true));
        Security security = ((SecurityItem) secItem.get()).getSecurity();
        assertThat(security.getIsin(), is("DE000A0J3UF6"));
        assertThat(security.getWkn(), is("A0J3UF"));
        assertThat(security.getName(), is("EARTH EXPLORAT.FDS UI EOR"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof BuySellEntryItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(BuySellEntry.class));
        BuySellEntry entry = (BuySellEntry) item.get().getSubject();

        assertThat(entry.getPortfolioTransaction().getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(entry.getAccountTransaction().getType(), is(AccountTransaction.Type.BUY));

        assertThat(entry.getPortfolioTransaction().getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 25_00L)));
        assertThat(entry.getPortfolioTransaction().getDateTime(), is(LocalDateTime.of(2017, 10, 16, 15, 24, 22)));
        assertThat(entry.getPortfolioTransaction().getShares(), is(Values.Share.factorize(0.95126)));
        assertThat(entry.getPortfolioTransaction().getUnitSum(Unit.Type.FEE), is(Money.of(CurrencyUnit.EUR, 61L)));
        assertThat(entry.getPortfolioTransaction().getGrossPricePerShare(),
                        is(Quote.of(CurrencyUnit.EUR, Values.Quote.factorize(25.6397))));
    }

    @Test
    public void testWertpapierKaufSparplan() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankKaufSparplan.txt"),
                        errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(2));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check security
        Optional<Item> secItem = results.stream().filter(i -> i instanceof SecurityItem).findFirst();
        assertThat(secItem.isPresent(), is(true));
        Security security = ((SecurityItem) secItem.get()).getSecurity();
        assertThat(security.getIsin(), is("PO6527623674"));
        assertThat(security.getWkn(), is("SP110Y"));
        assertThat(security.getName(), is("Sparplanname"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof BuySellEntryItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(BuySellEntry.class));
        BuySellEntry entry = (BuySellEntry) item.get().getSubject();

        assertThat(entry.getPortfolioTransaction().getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(entry.getAccountTransaction().getType(), is(AccountTransaction.Type.BUY));

        assertThat(entry.getPortfolioTransaction().getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 100_00L)));
        assertThat(entry.getPortfolioTransaction().getDateTime(), is(LocalDateTime.parse("2016-06-15T11:07:25")));
        assertThat(entry.getPortfolioTransaction().getShares(), is(Values.Share.factorize(6.43915)));
        assertThat(entry.getPortfolioTransaction().getUnitSum(Unit.Type.FEE), is(Money.of(CurrencyUnit.EUR, 0_00L)));
        assertThat(entry.getPortfolioTransaction().getGrossPricePerShare(),
                        is(Quote.of(CurrencyUnit.EUR, Values.Quote.factorize(15.53))));
    }

    @Test
    public void testWertpapierKaufIfSecurityIsPresent() throws IOException
    {
        Client client = new Client();
        Security s = new Security();
        s.setName("COMS.-MSCI WORL.T.U.ETF I");
        s.setWkn("ETF110");
        client.addSecurity(s);

        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(PDFInputFile.loadTestCase(getClass(), "ConsorsbankKauf1.txt"), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        // check buy sell transaction
        Item item = results.get(0);
        BuySellEntry entry = (BuySellEntry) item.getSubject();
        assertThat(entry.getPortfolioTransaction().getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, 5000_00L)));
    }

    @Test
    public void testNachtraeglicheVerlustverrechnung1() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(
                        PDFInputFile.loadTestCase(getClass(), "ConsorsbankNachtraeglicheVerlustverrechnung1.txt"),
                        errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof TransactionItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(AccountTransaction.class));
        AccountTransaction t = (AccountTransaction) item.get().getSubject();

        assertThat(t.getType(), is(AccountTransaction.Type.TAX_REFUND));

        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(90.61))));
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2017-07-10T00:00")));
    }

    @Test
    public void testNachtraeglicheVerlustverrechnung2() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(
                        PDFInputFile.loadTestCase(getClass(), "ConsorsbankNachtraeglicheVerlustverrechnung2.txt"),
                        errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof TransactionItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(AccountTransaction.class));
        AccountTransaction t = (AccountTransaction) item.get().getSubject();

        assertThat(t.getType(), is(AccountTransaction.Type.TAXES));

        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(0.1))));
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2017-07-10T00:00")));
    }

    @Test
    public void testNachtraeglicheVerlustverrechnung3() throws IOException
    {
        ConsorsbankPDFExtractor extractor = new ConsorsbankPDFExtractor(new Client());

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(
                        PDFInputFile.loadTestCase(getClass(), "ConsorsbankNachtraeglicheVerlustverrechnung3.txt"),
                        errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));

        // check buy sell transaction
        Optional<Item> item = results.stream().filter(i -> i instanceof TransactionItem).findFirst();
        assertThat(item.isPresent(), is(true));
        assertThat(item.get().getSubject(), instanceOf(AccountTransaction.class));
        AccountTransaction t = (AccountTransaction) item.get().getSubject();

        assertThat(t.getType(), is(AccountTransaction.Type.TAX_REFUND));

        assertThat(t.getMonetaryAmount(), is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(0))));
        assertThat(t.getDateTime(), is(LocalDateTime.parse("2017-07-10T00:00")));
    }

    private void checkCurrency(final String accountCurrency, AccountTransaction transaction)
    {
        Account account = new Account();
        account.setCurrencyCode(accountCurrency);
        Status status = new CheckCurrenciesAction().process(transaction, account);
        assertThat(status.getCode(), is(Code.OK));
    }
}
