package com.jstarcraft.rns.model.collaborative.ranking;

import java.util.Map;
import java.util.Properties;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import com.jstarcraft.ai.evaluate.ranking.AUCEvaluator;
import com.jstarcraft.ai.evaluate.ranking.MAPEvaluator;
import com.jstarcraft.ai.evaluate.ranking.MRREvaluator;
import com.jstarcraft.ai.evaluate.ranking.NDCGEvaluator;
import com.jstarcraft.ai.evaluate.ranking.NoveltyEvaluator;
import com.jstarcraft.ai.evaluate.ranking.PrecisionEvaluator;
import com.jstarcraft.ai.evaluate.ranking.RecallEvaluator;
import com.jstarcraft.core.utility.Configurator;
import com.jstarcraft.rns.model.collaborative.ranking.AoBPRModel;
import com.jstarcraft.rns.task.RankingTask;

public class AoBPRModelTestCase {

	@Test
	public void testRecommender() throws Exception {
	    Properties keyValues = new Properties();
        keyValues.load(this.getClass().getResourceAsStream("/data.properties"));
        keyValues.load(this.getClass().getResourceAsStream("/recommend/collaborative/ranking/aobpr-test.properties"));
        Configurator configuration = new Configurator(keyValues);
		RankingTask job = new RankingTask(AoBPRModel.class, configuration);
		Map<String, Float> measures = job.execute();
		Assert.assertThat(measures.get(AUCEvaluator.class.getSimpleName()), CoreMatchers.equalTo(0.9195788F));
		Assert.assertThat(measures.get(MAPEvaluator.class.getSimpleName()), CoreMatchers.equalTo(0.43754834F));
		Assert.assertThat(measures.get(MRREvaluator.class.getSimpleName()), CoreMatchers.equalTo(0.5987676F));
		Assert.assertThat(measures.get(NDCGEvaluator.class.getSimpleName()), CoreMatchers.equalTo(0.53109217F));
		Assert.assertThat(measures.get(NoveltyEvaluator.class.getSimpleName()), CoreMatchers.equalTo(17.760157F));
		Assert.assertThat(measures.get(PrecisionEvaluator.class.getSimpleName()), CoreMatchers.equalTo(0.33980674F));
		Assert.assertThat(measures.get(RecallEvaluator.class.getSimpleName()), CoreMatchers.equalTo(0.58792263F));
	}

}