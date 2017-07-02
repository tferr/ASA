//@UIService ui
//@OUTPUT ResultsTable table
import sholl.Profile
import sholl.ShollUtils
import sholl.gui.ShollPlot
import sholl.math.LinearProfileStats
import sholl.parsers.TabularParser


table = ShollUtils.csvSample()
parser = new TabularParser(table, "radii_um", "counts")
profile = parser.parse()
if (!parser.successful())
    ui.showDialog("Could not parse\n"+ csvFile)
//ui.show("ddaCsample.csv", table)


println "*** Analyzing CSV Demo ***"
lStats = new LinearProfileStats(profile)
plot = new ShollPlot(lStats)
plot.show()

// Determine polynomial of best fit. We'll wait
// .5s in order to animate the iterative fitting
rSq_highest = 0
pValue = 1
bestDegree = 1
println("*** Determining Best Fit [Degrees 1-30] ***")
for (degree in 1..3) {
    try {
        lStats.fitPolynomial(degree)
        sleep(500)
        plot.rebuild()
    } catch (Exception e) {
        println("  Could not fit degree ${degree}: ${e.getClass().getName()}")
        continue
    }
    pValue = lStats.getKStestOfFit()
    if (pValue < 0.05) {
        println("  Skipping degree ${degree}: Fitted data significantly different")
        continue
    }
    rSq = lStats.getRSquaredOfFit(true)
    if (rSq > rSq_highest) {
        rSq_highest = rSq
        bestDegree = degree
    }
}
println("  Best polynomial: " + bestDegree)
println("  Rsquared (adj.): " + rSq_highest)
println("  p-value (K-S test): " + pValue)

[false, true].each {
    println("*** Linear Sholl Stats ${it?"Fitted":"Sampled"} Data ***")
    println("  Min: " + lStats.getMin(it))
    println("  Max: " + lStats.getMax(it))
    println("  Mean: " + lStats.getMean(it))
    println("  Median: " + lStats.getMedian(it))
    println("  Sum: " + lStats.getSum(it))
    println("  Variance: " + lStats.getVariance(it))
    println("  Sum squared: " + lStats.getSumSq(it))
    println("  Intersect. radii: " + lStats.getIntersectingRadii(it))
    println("  I branches: " + lStats.getPrimaryBranches(it))
    println("  Ramification index: " + lStats.getRamificationIndex(it))
    println("  Centroid: " + lStats.getCentroid(it))
    println("  Centroid (polygon): " + lStats.getPolygonCentroid(it))
    println("  Enclosing radius: " + lStats.getEnclosingRadius(it, 1))
    println("  Maxima: " + lStats.getMaxima(it))
    println("  Centered maximum: " + lStats.getCenteredMaximum(it))
    println("  Kurtosis: " + lStats.getKurtosis(it))
    println("  Skewness: " + lStats.getSkewness(it))
}