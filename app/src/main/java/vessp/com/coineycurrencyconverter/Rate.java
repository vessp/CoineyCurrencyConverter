package vessp.com.coineycurrencyconverter;

/**
 * Basic name, value rate pair
 */

public class Rate
{
    public String name;
    public Double value;

    public Rate(String name, Double value)
    {
        this.name = name;
        this.value = value;
    }
}
