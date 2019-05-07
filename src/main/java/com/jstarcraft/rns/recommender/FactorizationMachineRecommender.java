package com.jstarcraft.rns.recommender;

import java.util.Map.Entry;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;

import com.jstarcraft.ai.data.DataInstance;
import com.jstarcraft.ai.data.DataModule;
import com.jstarcraft.ai.data.DataSpace;
import com.jstarcraft.ai.math.algorithm.probability.QuantityProbability;
import com.jstarcraft.ai.math.structure.DefaultScalar;
import com.jstarcraft.ai.math.structure.MathCalculator;
import com.jstarcraft.ai.math.structure.matrix.DenseMatrix;
import com.jstarcraft.ai.math.structure.vector.ArrayVector;
import com.jstarcraft.ai.math.structure.vector.DenseVector;
import com.jstarcraft.ai.math.structure.vector.MathVector;
import com.jstarcraft.ai.math.structure.vector.VectorScalar;
import com.jstarcraft.core.utility.KeyValue;
import com.jstarcraft.rns.configurator.Configuration;
import com.jstarcraft.rns.exception.RecommendationException;

/**
 * Factorization Machine Recommender
 *
 * Rendle, Steffen, et al., <strong>Fast Context-aware Recommendations with
 * Factorization Machines</strong>, SIGIR, 2011.
 *
 * @author Tang Jiaxi and Ma Chen
 */
// TODO 论文中需要支持组合特征(比如:历史评价过的电影),现在的代码并没有实现.
public abstract class FactorizationMachineRecommender extends ModelRecommender {

    protected DataModule marker;

    /**
     * global bias
     */
    protected float globalBias;
    /**
     * appender vector size: number of users + number of items + number of
     * contextual conditions
     */
    protected int numberOfFeatures;
    /**
     * number of factors
     */
    protected int numberOfFactors;

    /**
     * weight vector
     */
    protected DenseVector weightVector; // p
    /**
     * parameter matrix(featureFactors)
     */
    protected DenseMatrix featureFactors; // p x k
    /**
     * parameter matrix(rateFactors)
     */
    protected DenseMatrix actionFactors; // n x k
    /**
     * regularization term for weight and factors
     */
    protected float biasRegularization, weightRegularization, factorRegularization;

    /**
     * init mean
     */
    protected float initMean;

    /**
     * init standard deviation
     */
    protected float initStd;

    protected QuantityProbability distribution;

    protected int[] dimensionSizes;

    @Override
    public void prepare(Configuration configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        // TODO 暂时不支持连续特征,考虑将连续特征离散化.
        this.marker = model;
        dimensionSizes = new int[marker.getQualityOrder()];

        // TODO 考虑重构,在AbstractRecommender初始化
        numberOfActions = marker.getSize();
        maximumOfScore = configuration.getFloat("rec.recommender.maxrate", 12F);
        minimumOfScore = configuration.getFloat("rec.recommender.minrate", 0F);

        // initialize the parameters of FM
        // TODO 此处需要重构,外部索引与内部索引的映射转换
        for (int orderIndex = 0, orderSize = marker.getQualityOrder(); orderIndex < orderSize; orderIndex++) {
            Entry<Integer, KeyValue<String, Boolean>> term = marker.getOuterKeyValue(orderIndex);
            dimensionSizes[marker.getQualityInner(term.getValue().getKey())] = space.getQualityAttribute(term.getValue().getKey()).getSize();
            numberOfFeatures += dimensionSizes[marker.getQualityInner(term.getValue().getKey())];
        }

        numberOfFactors = configuration.getInteger("rec.factor.number");

        // init all weight with zero
        globalBias = 0;
        weightVector = DenseVector.valueOf(numberOfFeatures);

        // init factors with small value
        // TODO 此处需要重构
        initMean = configuration.getFloat("rec.init.mean", 0F);
        initStd = configuration.getFloat("rec.init.std", 0.1F);

        distribution = new QuantityProbability(JDKRandomGenerator.class, 0, NormalDistribution.class, initMean, initStd);
        featureFactors = DenseMatrix.valueOf(numberOfFeatures, numberOfFactors);
        featureFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });

        biasRegularization = configuration.getFloat("rec.fm.regw0", 0.01f);
        weightRegularization = configuration.getFloat("rec.fm.regW", 0.01f);
        factorRegularization = configuration.getFloat("rec.fm.regF", 10f);
    }

    /**
     * 获取特征向量
     * 
     * <pre>
     * 实际为One Hot Encoding(一位有效编码)
     * 详细原理与使用参考:http://blog.csdn.net/pipisorry/article/details/61193868
     * </pre>
     * 
     * @param featureIndexes
     * @return
     */
    protected MathVector getFeatureVector(DataInstance instance) {
        int orderSize = instance.getQualityOrder();
        int[] keys = new int[orderSize];
        int cursor = 0;
        for (int orderIndex = 0; orderIndex < orderSize; orderIndex++) {
            keys[orderIndex] += cursor + instance.getQualityFeature(orderIndex);
            cursor += dimensionSizes[orderIndex];
        }
        ArrayVector vector = new ArrayVector(numberOfFeatures, keys);
        vector.setValues(1F);
        return vector;
    }

    /**
     * Predict the rating given a sparse appender vector.
     * 
     * @param userIndex     user Id
     * @param itemIndex     item Id
     * @param featureVector the given vector to predict.
     *
     * @return predicted rating
     * @throws RecommendationException if error occurs
     */
    protected float predict(DefaultScalar scalar, MathVector featureVector) {
        float value = 0;
        // global bias
        value += globalBias;
        // 1-way interaction
        value += scalar.dotProduct(weightVector, featureVector).getValue();

        // 2-way interaction
        for (int factorIndex = 0; factorIndex < numberOfFactors; factorIndex++) {
            float scoreSum = 0F;
            float predictSum = 0F;
            for (VectorScalar vectorTerm : featureVector) {
                float featureValue = vectorTerm.getValue();
                int featureIndex = vectorTerm.getIndex();
                float predictValue = featureFactors.getValue(featureIndex, factorIndex);

                scoreSum += predictValue * featureValue;
                predictSum += predictValue * predictValue * featureValue * featureValue;
            }
            value += (scoreSum * scoreSum - predictSum) / 2F;
        }

        return value;
    }

    @Override
    public float predict(DataInstance instance) {
        DefaultScalar scalar = DefaultScalar.getInstance();
        // TODO 暂时不支持连续特征,考虑将连续特征离散化.
        MathVector featureVector = getFeatureVector(instance);
        return predict(scalar, featureVector);
    }

}