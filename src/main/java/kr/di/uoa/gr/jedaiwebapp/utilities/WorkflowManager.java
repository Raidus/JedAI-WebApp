package kr.di.uoa.gr.jedaiwebapp.utilities;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.scify.jedai.blockbuilding.IBlockBuilding;
import org.scify.jedai.blockprocessing.IBlockProcessing;
import org.scify.jedai.datamodel.AbstractBlock;
import org.scify.jedai.datamodel.EntityProfile;
import org.scify.jedai.datamodel.EquivalenceCluster;
import org.scify.jedai.datamodel.SimilarityPairs;
import org.scify.jedai.entityclustering.IEntityClustering;
import org.scify.jedai.entitymatching.IEntityMatching;
import org.scify.jedai.schemaclustering.ISchemaClustering;
import org.scify.jedai.utilities.BlocksPerformance;
import org.scify.jedai.utilities.ClustersPerformance;
import org.scify.jedai.utilities.datastructures.AbstractDuplicatePropagation;
import org.scify.jedai.utilities.enumerations.BlockBuildingMethod;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import gnu.trove.list.TIntList;
import gnu.trove.map.TObjectIntMap;
import kr.di.uoa.gr.jedaiwebapp.utilities.events.EventPublisher;
import kr.di.uoa.gr.jedaiwebapp.datatypes.EntityProfileNode;
import kr.di.uoa.gr.jedaiwebapp.datatypes.MethodModel;
import kr.di.uoa.gr.jedaiwebapp.utilities.configurations.DynamicMethodConfiguration;
import kr.di.uoa.gr.jedaiwebapp.utilities.configurations.JedaiOptions;
import kr.di.uoa.gr.jedaiwebapp.utilities.configurations.MethodConfigurations;

public class WorkflowManager {
	
	private final static int NO_OF_TRIALS = 100;

	public static  String er_mode = null;
	public static List<EntityProfile> profilesD1 = null;
	public static List<EntityProfile> profilesD2 = null;
	public static AbstractDuplicatePropagation ground_truth = null;

	public static ISchemaClustering schema_clustering = null;
	public static IBlockProcessing comparison_cleaning = null;
	public static IEntityMatching entity_matching = null;
	public static IEntityClustering entity_clustering = null;
	public static List<IBlockBuilding> block_building = null;
	public static List<IBlockProcessing> block_cleaning = null;
	
	private static EquivalenceCluster[] entityClusters = null;
	
	private static EventPublisher eventPublisher;
	private static WorkflowDetailsManager details_manager ;

	public static int workflowConfigurationsID = -1;
	

	@Bean
	EventPublisher publisherBean () {
        return new EventPublisher();
    }
	
	@Bean
	SSE_Manager SSE_ManagerBean () {
		return new SSE_Manager();
	}
	
	public static void clean() {
		
		workflowConfigurationsID = -1;
		schema_clustering = null;
		comparison_cleaning = null;
		entity_matching = null;
		entity_clustering = null;
		block_building = null;
		block_cleaning = null;
		entityClusters = null;
		
	}
	
	public static void setSchemaClustering(MethodModel schema_clustering) {
		if (!schema_clustering.getLabel().equals(JedaiOptions.NO_SCHEMA_CLUSTERING)) {
			
			if(!schema_clustering.getConfiguration_type().equals(JedaiOptions.MANUAL_CONFIG)) 			
				WorkflowManager.schema_clustering = MethodConfigurations.getSchemaClusteringMethodByName(schema_clustering.getLabel());
			else
				WorkflowManager.schema_clustering = DynamicMethodConfiguration.configureSchemaClusteringMethod(
						schema_clustering.getLabel(),
						schema_clustering.getParameters());
	                    
			System.out.println("SC: " + WorkflowManager.schema_clustering);
		}	
	}
	
	public static void setComparisonCleaning(MethodModel comparison_cleaning) {
		
		if (!comparison_cleaning.getLabel().equals(JedaiOptions.NO_CLEANING)) {
			if(!comparison_cleaning.getConfiguration_type().equals(JedaiOptions.MANUAL_CONFIG)) 	
					WorkflowManager.comparison_cleaning = MethodConfigurations.getMethodByName(comparison_cleaning.getLabel());
			else 
				WorkflowManager.comparison_cleaning = DynamicMethodConfiguration.configureComparisonCleaningMethod(
        			comparison_cleaning.getLabel(),
        			comparison_cleaning.getParameters() );
		
		}
	}
	
	public static void setEntityMatching(MethodModel entity_matching) {
		if(!entity_matching.getConfiguration_type().equals(JedaiOptions.MANUAL_CONFIG)) 	
			WorkflowManager.entity_matching = DynamicMethodConfiguration
                    .configureEntityMatchingMethod(entity_matching.getLabel(), null);
         else 
        	 WorkflowManager.entity_matching = DynamicMethodConfiguration
                    .configureEntityMatchingMethod(entity_matching.getLabel(), entity_matching.getParameters());
        
		System.out.println("EM: " + WorkflowManager.entity_matching);
	}
	
	public static void setEntityClustering(MethodModel entity_clustering) {
		if(!entity_clustering.getConfiguration_type().equals(JedaiOptions.MANUAL_CONFIG)) 
			WorkflowManager.entity_clustering = MethodConfigurations.getEntityClusteringMethod(entity_clustering.getLabel());
         else 
        	 WorkflowManager.entity_clustering = DynamicMethodConfiguration.configureEntityClusteringMethod(entity_clustering.getLabel(), entity_clustering.getParameters());
        
		System.out.println("EC: " + WorkflowManager.entity_clustering);
	}
	
	public static void addBlockBuildingMethod(MethodModel method) {
		
		if(WorkflowManager.block_building == null) WorkflowManager.block_building = new ArrayList<IBlockBuilding>();

    	BlockBuildingMethod blockBuilding_method = MethodConfigurations.blockBuildingMethods.get(method.getLabel());
       
        IBlockBuilding blockBuildingMethod;
        if (!method.getConfiguration_type().equals(JedaiOptions.MANUAL_CONFIG)) 
            
            blockBuildingMethod = BlockBuildingMethod.getDefaultConfiguration(blockBuilding_method);
         else 
        	 blockBuildingMethod = DynamicMethodConfiguration.configureBlockBuildingMethod(blockBuilding_method, method.getParameters());
        
        WorkflowManager.block_building.add(blockBuildingMethod);
	}
	
	public static void addBlockCleaningMethod(MethodModel method) {
		if(WorkflowManager.block_cleaning == null) WorkflowManager.block_cleaning = new ArrayList<IBlockProcessing>();
		
		IBlockProcessing blockCleaning_method;
        if (!method.getConfiguration_type().equals(JedaiOptions.MANUAL_CONFIG)) 
        	blockCleaning_method = MethodConfigurations.getMethodByName(method.getLabel());
          else 
         	 blockCleaning_method = DynamicMethodConfiguration.configureBlockCleaningMethod(
             		method.getLabel(), method.getParameters());

         WorkflowManager.block_cleaning.add(blockCleaning_method);
	}
	
	
	  /**
     * Run a block building method and return its blocks
     *
     * @param er_mode     Entity Resolution type
     * @param clusters   Clusters from schema clustering, if applicable (can be null)
     * @param profilesD1 List of profiles from the 1st dataset
     * @param profilesD2 List of profiles from the 2nd dataset
     * @param bb         Block building method instance to get blocks with
     * @return List of blocks generated by block building method
     */
    private static List<AbstractBlock> runBlockBuilding(String er_mode, TObjectIntMap<String>[] clusters,
                                                 List<EntityProfile> profilesD1, List<EntityProfile> profilesD2,
                                                 IBlockBuilding bb) {
        if (er_mode.equals(JedaiOptions.DIRTY_ER)) {
            if (clusters == null) {
                // Dirty ER without schema clustering
                return bb.getBlocks(profilesD1);
            } else {
                // Dirty ER with schema clustering
                return bb.getBlocks(profilesD1, null, clusters);
            }
        } else {
            if (clusters == null) {
                // Clean-clean ER without schema clustering
                return bb.getBlocks(profilesD1, profilesD2);
            } else {
                // Clean-clean ER with schema clustering
                return bb.getBlocks(profilesD1, profilesD2, clusters);
            }
        }
    }
    
    
    
    /**
     * Process blocks using a given block processing method
     *
     * @param duProp        Duplicate propagation (from ground-truth)
     * @param finalRun      Set to true to print clusters performance
     * @param blocks        Blocks to process
     * @param currentMethod Method to process the blocks with
     * @return Processed list of blocks
     */
    private static Triplet<List<AbstractBlock>, BlocksPerformance, Double> runBlockProcessing(AbstractDuplicatePropagation duProp, boolean finalRun,
                    List<AbstractBlock> blocks, IBlockProcessing currentMethod) {
        double overheadStart;
        double overheadEnd;
        BlocksPerformance blp = null;
        overheadStart = System.currentTimeMillis();

        if (!blocks.isEmpty()) {
            blocks = currentMethod.refineBlocks(blocks);
            overheadEnd = System.currentTimeMillis();

            if (finalRun) {
                // Print blocks performance
                blp = new BlocksPerformance(blocks, duProp);
                blp.setStatistics();
                details_manager.print_BlockBuildingPerformance(blp, (overheadEnd - overheadStart)/1000, currentMethod.getMethodConfiguration(),  currentMethod.getMethodName());
                
            }
            return new Triplet<>(blocks, blp, (overheadEnd - overheadStart)/1000);
        }
        return new Triplet<>(blocks, null, 0.0);
    }
    
    
    /**
     * Get total comparisons that will be made for a list of blocks
     *
     * @param blocks List of blocks
     * @return Number of comparisons
     */
    private static double getTotalComparisons(List<AbstractBlock> blocks) {
        double originalComparisons = 0;
        originalComparisons = blocks.stream()
                .map(AbstractBlock::getNoOfComparisons)
                .reduce(originalComparisons, (accumulator, _item) -> accumulator + _item);
        System.out.println("Original comparisons\t:\t" + originalComparisons);
        return originalComparisons;
    }
    
    
    /**
     * Optimize a given block processing method randomly using the given list of blocks.
     * Modifies the original block processing object and sets it to use the best found
     * random configuration.
     *
     * @param bp     Block processing method object
     * @param blocks Blocks to optimize with
     * @param random If true will use random search, otherwise grid
     */
    private static void optimizeBlockProcessing(IBlockProcessing bp, List<AbstractBlock> blocks, boolean random) {
        List<AbstractBlock> cleanedBlocks;
        double bestA = 0;
        int bestIteration = 0;
        double originalComparisons = getTotalComparisons(blocks);

        int iterationsNum = random ? NO_OF_TRIALS : bp.getNumberOfGridConfigurations();
        for (int j = 0; j < iterationsNum; j++) {
            if (random) {
                bp.setNextRandomConfiguration();
            } else {
                bp.setNumberedGridConfiguration(j);
            }
            cleanedBlocks = bp.refineBlocks(blocks);
            if (cleanedBlocks.isEmpty()) {
                continue;
            }

            BlocksPerformance blp = new BlocksPerformance(cleanedBlocks, ground_truth);
            blp.setStatistics();
            double recall = blp.getPc();
            double rr = 1 - blp.getAggregateCardinality() / originalComparisons;
            double a = rr * recall;
            if (bestA < a) {
                bestIteration = j;
                bestA = a;
            }
        }
        System.out.println("\n\nBest iteration\t:\t" + bestIteration);
        System.out.println("Best performance\t:\t" + bestA);

        if (random) {
            bp.setNumberedRandomConfiguration(bestIteration);
        } else {
            bp.setNumberedGridConfiguration(bestIteration);
        }
    }
	
	
	
	/**
	 * Run a workflow with the given methods and return its ClustersPerformance
	 *
	 * @param final_run true if this is the final run
	 * @return  the Cluster Performance and the performances of each step
	 * */
	public static Pair<ClustersPerformance, List<Triplet<String, BlocksPerformance, Double>>>
	runWorkflow(boolean final_run, AtomicBoolean interrupted)  {
		try {	
					
			List<Triplet<String, BlocksPerformance, Double>> performances = new ArrayList<>();
			
			String event_name="execution_step";
			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WorkflowManager.class);
			eventPublisher = context.getBean(EventPublisher.class);
			details_manager = new WorkflowDetailsManager();
			
			if(!final_run)
				eventPublisher.publish("Processing Automatic Configurations", event_name);
			
			// Print profile entities statistics
			if(final_run) {
				if(er_mode.equals(JedaiOptions.DIRTY_ER))
					details_manager.print_Sentence("Input Entity Profiles", profilesD1.size());
				else {
					details_manager.print_Sentence("Input Entity Profiles 1", profilesD1.size());
					details_manager.print_Sentence("Input Entity Profiles 2", profilesD2.size());
				}
				details_manager.print_Sentence("Existing Duplicates", ground_truth.getDuplicates().size());
			}
			
			//the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}			
			
			// Run Schema Clustering
			TObjectIntMap<String>[] clusters = null;
	        if (schema_clustering != null) {
	        	if(final_run) 
	    			eventPublisher.publish("Schema Clustering", event_name);
	    		
	            if (er_mode.equals(JedaiOptions.DIRTY_ER)) {
	                clusters = schema_clustering.getClusters(profilesD1);
	            } else {
	                clusters = schema_clustering.getClusters(profilesD1, profilesD2);
	            }
	        }
	        
	        //the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}		
	        	        
			double overheadStart;
	        double overheadEnd;
	        BlocksPerformance blp;
	
	        // run Block Building
	        if(final_run) 
				eventPublisher.publish("Block Building", event_name);
	        
	        List<AbstractBlock> blocks = new ArrayList<>();
	        for (IBlockBuilding bb : block_building) {
	        	
	        	//the process was stopped by the user
				if (interrupted.get()) {
					eventPublisher.publish("", event_name);
					return null;
				}		
	        	
	            // Start time measurement
	            overheadStart = System.currentTimeMillis();
	
	            // Run the method
	            blocks.addAll(runBlockBuilding(er_mode, clusters, profilesD1, profilesD2, bb));
	
	            // Get blocks performance to print
	            overheadEnd = System.currentTimeMillis();
	            blp = new BlocksPerformance(blocks, ground_truth);
	            blp.setStatistics();
	            
	            if (final_run) {
	                // print block Building performance
	                details_manager.print_BlockBuildingPerformance(blp, 
	                		(overheadEnd - overheadStart)/1000, 
	                		bb.getMethodConfiguration(), 
	                		bb.getMethodName());
	                performances.add(new Triplet<>(bb.getMethodName(), blp, (overheadEnd - overheadStart)/1000));
	            }
	        }
	        
	        if(final_run)
	        	details_manager.print_Sentence("Original blocks\t:\t", blocks.size()); 
	
	        
	        // Run Block Cleaning
	        if (block_cleaning != null && !block_cleaning.isEmpty()) {
	        	
	        	//the process was stopped by the user
				if (interrupted.get()) {
					eventPublisher.publish("", event_name);
					return null;
				}		
	        	
	            if(final_run) 
	    			eventPublisher.publish("Block Cleaning", event_name);
	            
	            // Execute the methods
	            for (IBlockProcessing currentMethod : block_cleaning) {
	            	
	            	overheadStart = System.currentTimeMillis();
	            	
	                Triplet<List<AbstractBlock>, BlocksPerformance, Double> p = runBlockProcessing(ground_truth, final_run, blocks, currentMethod);
	                blocks = p.getValue0();
	                if (final_run)
	                	performances.add(new Triplet<>(currentMethod.getMethodName(), p.getValue1(), p.getValue2()));
		            
	                if (blocks.isEmpty()) {
	                    return null;
	                }
	            }
	        }
	
	        // Run Comparison Cleaning     
	        if (comparison_cleaning != null) {
	        	if(final_run) 
	    			eventPublisher.publish("Comparison Cleaning", event_name);
	    		
	        	//the process was stopped by the user
				if (interrupted.get()) {
					eventPublisher.publish("", event_name);
					return null;
				}		
	        	
				Triplet<List<AbstractBlock>, BlocksPerformance, Double> p = runBlockProcessing(ground_truth, final_run, blocks, comparison_cleaning);
				blocks = p.getValue0();
				if (final_run)
                	performances.add(new Triplet<>(comparison_cleaning.getMethodName(), p.getValue1(), p.getValue2()));
				
	            if (blocks.isEmpty()) {
	                return null;
	            }
	        }
	
	        
	        // Run Entity Matching
	        SimilarityPairs simPairs;
	        if (entity_matching == null)
	            throw new Exception("Entity Matching method is null!");
	                
	        if(final_run) 
				eventPublisher.publish("Entity Matching", event_name);
	        
	        //the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}		
	        
			overheadStart = System.currentTimeMillis();
			
	        if (er_mode.equals(JedaiOptions.DIRTY_ER)) 
	            simPairs = entity_matching.executeComparisons(blocks, profilesD1);
	        else 
	            simPairs = entity_matching.executeComparisons(blocks, profilesD1, profilesD2);
	        
	        overheadEnd = System.currentTimeMillis();
	        if(final_run) {
	        	String msg = "Entity Matching\nMethod: " + entity_matching.getMethodName() +"\nTotal Time: ";
	        	details_manager.print_Sentence(msg, (overheadEnd - overheadStart)/1000);
	        }
	
	        // Run Entity Clustering
	        if(final_run) 
				eventPublisher.publish("Entity Clustering", event_name);
	        
	        overheadStart = System.currentTimeMillis();
	        
	        //the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}		
	        
	        entityClusters = entity_clustering.getDuplicates(simPairs);
	
	        // Print clustering performance
	        overheadEnd = System.currentTimeMillis();
	        ClustersPerformance clp = new ClustersPerformance(entityClusters, ground_truth);
	        clp.setStatistics();        
	        if (final_run)
	        	details_manager.print_ClustersPerformance(clp, 
	        			(overheadEnd - overheadStart)/1000, 
	        			entity_clustering.getMethodName(), 
	        			entity_clustering.getMethodConfiguration());
	
	        eventPublisher.publish("", event_name);
	        
	        
	        return new Pair<>(clp, performances);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			setErrorMessage(e.getMessage());
			return null;
		}
        catch(Exception e) {
        	e.printStackTrace();
        	setErrorMessage(e.getMessage());
			return null;
		}
        
	}
	
	
	// TODO return performances from runStepByStepWorkflow
	
	/**
     * Run a step by step workflow, using random or grid search based on the given parameter.
     *
     * @param random      If true, will use random search. Otherwise, grid.
     * @return ClustersPerformance of the workflow result
     */
	public static Pair<ClustersPerformance, List<Triplet<String, BlocksPerformance, Double>>> 
	runStepByStepWorkflow(Map<String, Object> methodsConfig, boolean random, AtomicBoolean interrupted) {
	
		try {
			
			double bestA = 0, time1, time2, originalComparisons;
		    int bestIteration = 0, iterationsNum;
		    BlocksPerformance blp;
		    List<Triplet<String, BlocksPerformance, Double>> performances = new ArrayList<>();
		    
		    
			String event_name="execution_step";
			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WorkflowManager.class);
			eventPublisher = context.getBean(EventPublisher.class);
			details_manager = new WorkflowDetailsManager();
			
			// Print profile entities statistics
			if(er_mode.equals(JedaiOptions.DIRTY_ER))
				details_manager.print_Sentence("Input Entity Profiles", profilesD1.size());
			else {
				details_manager.print_Sentence("Input Entity Profiles 1", profilesD1.size());
				details_manager.print_Sentence("Input Entity Profiles 2", profilesD2.size());
			}
			details_manager.print_Sentence("Existing Duplicates", ground_truth.getDuplicates().size());
			 
			
			
			//the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}		
			
			// Schema Clustering local optimization
		    TObjectIntMap<String>[] scClusters = null;
		    if (schema_clustering != null) {
	    		eventPublisher.publish("Schema Clustering", event_name);
		
		        // Run Schema Clustering 
		        if (er_mode.equals(JedaiOptions.DIRTY_ER)) {
		            scClusters = schema_clustering.getClusters(profilesD1);
		        } else {
		            scClusters = schema_clustering.getClusters(profilesD1, profilesD2);
		        }
		    }
		    
		    //the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}		
		    
		    final List<AbstractBlock> blocks = new ArrayList<>();
		    if (block_building != null && !block_building.isEmpty()) {	
		    	
		    	List<MethodModel> bb_methods = (List<MethodModel>) methodsConfig.get(JedaiOptions.BLOCK_BUILDING);
		    	int index = 0;
		    	for (IBlockBuilding bb : block_building) {
		    		
		    		//the process was stopped by the user
					if (interrupted.get()) {
						eventPublisher.publish("", event_name);
						return null;
					}		
		    		
		    		time1 = System.currentTimeMillis();
		            if (bb_methods.get(index).getConfiguration_type().equals(JedaiOptions.AUTOMATIC_CONFIG)) {
		            	
		            	// Block Building local optimization
		        	    eventPublisher.publish("Block Building Optimizations", event_name);
		        	    
		        	    //the process was stopped by the user
		    			if (interrupted.get()) {
		    				eventPublisher.publish("", event_name);
		    				return null;
		    			}		
		        	    
		                if (er_mode.equals(JedaiOptions.DIRTY_ER)) {
		                    originalComparisons = profilesD1.size() * profilesD1.size();
		                } else {
		                    originalComparisons = ((double) profilesD1.size()) * profilesD2.size();
		                }
		
		                iterationsNum = random ? NO_OF_TRIALS : bb.getNumberOfGridConfigurations();
		
		                for (int j = 0; j < iterationsNum; j++) {
		                	
		                	//the process was stopped by the user
		        			if (interrupted.get()) {
		        				eventPublisher.publish("", event_name);
		        				return null;
		        			}		
		        			
		                    // Set next configuration
		                    if (random) {
		                        bb.setNextRandomConfiguration();
		                    } else {
		                        bb.setNumberedGridConfiguration(j);
		                    }
		
		                    // Process the blocks
		                    final List<AbstractBlock> originalBlocks = new ArrayList<>(blocks);
		                    originalBlocks.addAll(runBlockBuilding(er_mode, scClusters, profilesD1, profilesD2, bb));
		
		                    if (originalBlocks.isEmpty()) {
		                        continue;
		                    }
		
		                    final BlocksPerformance methodBlp = new BlocksPerformance(originalBlocks, ground_truth);
		                    methodBlp.setStatistics();
		                    double recall = methodBlp.getPc();
		                    double rr = 1 - methodBlp.getAggregateCardinality() / originalComparisons;
		                    double a = rr * recall;
		                    if (bestA < a) {
		                        bestIteration = j;
		                        bestA = a;
		                    }
		                }
		                details_manager.print_Sentence("\nBest iteration", bestIteration);
		                details_manager.print_Sentence("Best performance", bestA);
		
		                // Set final block building parameters
		                if (random) {
		                    bb.setNumberedRandomConfiguration(bestIteration);
		                } else {
		                    bb.setNumberedGridConfiguration(bestIteration);
		                }
		            }
		            
		            
		            // Process the blocks with block building
		            eventPublisher.publish("Block Building", event_name);
		            
		            if (er_mode.equals(JedaiOptions.DIRTY_ER)) {
		                blocks.addAll(bb.getBlocks(profilesD1));
		            } else {
		                blocks.addAll(bb.getBlocks(profilesD1, profilesD2));
		            }
		
		            time2 = System.currentTimeMillis();
		            	
		            blp = new BlocksPerformance(blocks, ground_truth);
		            blp.setStatistics();
		            details_manager.print_BlockBuildingPerformance(blp, 
		            		time2 - time1, 
	                		bb.getMethodConfiguration(), 
	                		bb.getMethodName());
		            
		            performances.add(new Triplet<>(bb.getMethodName(), blp, time2 - time1));
		
		            index++;
		        }
		    }
		    
		    //the process was stopped by the user
			if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}		
		
		    // Block Cleaning methods local optimization	
		    List<AbstractBlock> cleanedBlocks = blocks;
		    if (block_cleaning != null && !block_cleaning.isEmpty()) {
		    	List<MethodModel> bp_methods = (List<MethodModel>) methodsConfig.get(JedaiOptions.BLOCK_CLEANING);
		    	int index = 0;
		        for (IBlockProcessing bp: block_cleaning) {	
		        	
		        	//the process was stopped by the user
					if (interrupted.get()) {
						eventPublisher.publish("", event_name);
						return null;
					}		
		           
		        	// Start time measurement
		            time1 = System.currentTimeMillis();
		
		            // Check if we should configure this method automatically
		            if (bp_methods.get(index).getConfiguration_type().equals(JedaiOptions.AUTOMATIC_CONFIG)) {
		                // Optimize the method
		            	eventPublisher.publish("Block Cleaning Optimizations", event_name);
		            	//the process was stopped by the user
		    				
		                optimizeBlockProcessing(bp, blocks, random);
		                
		                if (interrupted.get()) {
		    				eventPublisher.publish("", event_name);
		    				return null;
		    			}	
		            }
		
		            // Process blocks with this method
		            eventPublisher.publish("Block Cleaning", event_name);
		            cleanedBlocks = bp.refineBlocks(blocks);
		
		            // Measure milliseconds it took to optimize & run method
		            time2 = System.currentTimeMillis();
		
		            blp = new BlocksPerformance(cleanedBlocks, ground_truth);
		            blp.setStatistics();
		            details_manager.print_BlockBuildingPerformance(blp, 
		            		time2 - time1, 
	                		bp.getMethodConfiguration(), 
	                		bp.getMethodName());
		            
		            performances.add(new Triplet<>(bp.getMethodName(), blp, time2 - time1));
		            
		            // Increment index
		            index++;
		        }
		    }
		    
		    if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}	

		    // Comparison Cleaning local optimization
		    time1 = System.currentTimeMillis();
		    List<AbstractBlock> finalBlocks;
		    MethodModel cc_method = (MethodModel) methodsConfig.get(JedaiOptions.COMPARISON_CLEANING);
		    if (cc_method.getConfiguration_type().equals(JedaiOptions.AUTOMATIC_CONFIG)) {
		    	eventPublisher.publish("Comparison Cleaning Optimizations", event_name);
		        optimizeBlockProcessing(comparison_cleaning, cleanedBlocks, random);
		    }	
		    
		    if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}	
		    
		    eventPublisher.publish("Comparison Cleaning", event_name);
		    finalBlocks = comparison_cleaning.refineBlocks(cleanedBlocks);
		    time2 = System.currentTimeMillis();
		    
		    if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}	
	
		    blp = new BlocksPerformance(finalBlocks, ground_truth);
		    blp.setStatistics();
		    details_manager.print_BlockBuildingPerformance(blp, 
	        		time2 - time1, 
	        		comparison_cleaning.getMethodConfiguration(), 
	        		comparison_cleaning.getMethodName());
		    performances.add(new Triplet<>(comparison_cleaning.getMethodName(), blp, time2 - time1));
		
		    
		    
		    // Entity Matching & Clustering local optimization
		    time1 = System.currentTimeMillis();
		    MethodModel em_method = (MethodModel) methodsConfig.get(JedaiOptions.ENTITY_MATHCING);
		    MethodModel ec_method = (MethodModel) methodsConfig.get(JedaiOptions.ENTITY_CLUSTERING);
		    boolean matchingAutomatic = em_method.getConfiguration_type().equals(JedaiOptions.AUTOMATIC_CONFIG);
		    boolean clusteringAutomatic = ec_method.getConfiguration_type().equals(JedaiOptions.AUTOMATIC_CONFIG);
		    
		    if (matchingAutomatic || clusteringAutomatic) {
		        // Show message that we are doing optimization based on the selected options
		        String optimizationMsg = (matchingAutomatic ? "Matching" : "") +
		                (matchingAutomatic && clusteringAutomatic ? " & " : "") +
		                (clusteringAutomatic ? "Clustering" : "");
		        eventPublisher.publish("Entity " + optimizationMsg + " Optimizations", event_name);
		
		        double bestFMeasure = 0;
		
		        // Check if we are using random search or grid search
		        if (random) {
		            bestIteration = 0;
		
		            // Optimize entity matching and clustering with random search
		            for (int j = 0; j < NO_OF_TRIALS; j++) {
		                // Set entity matching parameters automatically if needed
		                if (matchingAutomatic) 
		                    entity_matching.setNextRandomConfiguration();
		                
		                final SimilarityPairs sims = entity_matching.executeComparisons(finalBlocks, profilesD1, profilesD2);
		
		                // Set entity clustering parameters automatically if needed
		                if (clusteringAutomatic) 
		                    entity_clustering.setNextRandomConfiguration();
		                
		                final EquivalenceCluster[] clusters = entity_clustering.getDuplicates(sims);
		
		                final ClustersPerformance clp = new ClustersPerformance(clusters, ground_truth);
		                clp.setStatistics();
		                double fMeasure = clp.getFMeasure();
		                if (bestFMeasure < fMeasure) {
		                    bestIteration = j;
		                    bestFMeasure = fMeasure;
		                }
		                
		                if (interrupted.get()) {
		    				eventPublisher.publish("", event_name);
		    				return null;
		    			}	
		            }
		            details_manager.print_Sentence("\nBest Iteration", bestIteration);
		            details_manager.print_Sentence("Best FMeasure", bestFMeasure);
		
		            time1 = System.currentTimeMillis();
		
		            if (interrupted.get()) {
	    				eventPublisher.publish("", event_name);
	    				return null;
	    			}	
		            // Set the best iteration's parameters to the methods that should be automatically configured
		            if (matchingAutomatic) 
		            	entity_matching.setNumberedRandomConfiguration(bestIteration);
		            
		            if (interrupted.get()) {
	    				eventPublisher.publish("", event_name);
	    				return null;
	    			}	
		            
		            if (clusteringAutomatic) 
		            	entity_clustering.setNumberedRandomConfiguration(bestIteration);
		            
		        } else {
		
		            int bestInnerIteration = 0;
		            int bestOuterIteration = 0;
		
		            // Get number of loops for each
		            int outerLoops = (matchingAutomatic) ? entity_matching.getNumberOfGridConfigurations() : 1;
		            int innerLoops = (clusteringAutomatic) ? entity_clustering.getNumberOfGridConfigurations() : 1;
		
		            // Iterate all entity matching configurations
		            for (int j = 0; j < outerLoops; j++) {
		                if (matchingAutomatic) {
		                    entity_matching.setNumberedGridConfiguration(j);
		                }
		                final SimilarityPairs sims = entity_matching.executeComparisons(finalBlocks, profilesD1, profilesD2);
		
		                // Iterate all entity clustering configurations
		                for (int k = 0; k < innerLoops; k++) {
		                    if (clusteringAutomatic) {
		                        entity_clustering.setNumberedGridConfiguration(k);
		                    }
		                    final EquivalenceCluster[] clusters = entity_clustering.getDuplicates(sims);
		
		                    final ClustersPerformance clp = new ClustersPerformance(clusters, ground_truth);
		                    clp.setStatistics();
		                    double fMeasure = clp.getFMeasure();
		                    if (bestFMeasure < fMeasure) {
		                        bestInnerIteration = k;
		                        bestOuterIteration = j;
		                        bestFMeasure = fMeasure;
		                    }
		                }
		                if (interrupted.get()) {
		    				eventPublisher.publish("", event_name);
		    				return null;
		    			}	
		            }
		            eventPublisher.publish("\nBest Inner Iteration", String.valueOf(bestInnerIteration));
		            eventPublisher.publish("Best Outer Iteration", String.valueOf(bestOuterIteration));
		            eventPublisher.publish("Best FMeasure", String.valueOf(bestFMeasure));
		            
		            if (interrupted.get()) {
	    				eventPublisher.publish("", event_name);
	    				return null;
	    			}	
		
		            // Set the best iteration's parameters to the methods that should be automatically configured
		            if (matchingAutomatic) 
		                entity_matching.setNumberedGridConfiguration(bestOuterIteration);
		            
		            if (interrupted.get()) {
	    				eventPublisher.publish("", event_name);
	    				return null;
	    			}	
		            
		            if (clusteringAutomatic) 
		            	entity_clustering.setNumberedGridConfiguration(bestInnerIteration);
		        }
		    }
		    
		    if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}	
		    
		    // Run entity matching with final configuration
		    eventPublisher.publish("Entity Matching", event_name);
		    final SimilarityPairs sims = entity_matching.executeComparisons(finalBlocks, profilesD1, profilesD2);
		
		    if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}	
		    
		    // Run entity clustering with final configuration
		    eventPublisher.publish("Entity Clustering", event_name);
		    
		    if (interrupted.get()) {
				eventPublisher.publish("", event_name);
				return null;
			}	
		    
		    entityClusters = entity_clustering.getDuplicates(sims);
		
		    time2 = System.currentTimeMillis();
	
		
		    final ClustersPerformance clp = new ClustersPerformance(entityClusters, ground_truth);
		    clp.setStatistics();
		    // TODO: Could set the entire configuration details instead of entity clustering method name & config.	
		    details_manager.print_ClustersPerformance(clp, 
		    		time2 - time1,
	    			entity_clustering.getMethodName(), 
	    			entity_clustering.getMethodConfiguration());
		    
		    eventPublisher.publish("", event_name);
		    return new Pair<>(clp, performances);
		}
		catch(Exception e) {
			e.printStackTrace();
			setErrorMessage(e.getMessage());
			return null;
		}
	}
	
	
	
	/**
     * send the error message to the front-end
     *
     */
	public static void setErrorMessage(String error_msg) {
		if (eventPublisher == null) {
			AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WorkflowManager.class);
			eventPublisher = context.getBean(EventPublisher.class);
		}
		eventPublisher.publish(error_msg, "exception");
	}
    
    
    
    
	/**
     * Construct a list containing the detected duplicates
     * 
     * @return the list of the detected duplicates
     */
	public static List<List<EntityProfileNode>> getDetectedDuplicates(){
		
		List<List<EntityProfileNode>> duplicates = new ArrayList<>();
		
		for (EquivalenceCluster ec : ground_truth.getDetectedEquivalenceClusters()) {
			if (er_mode.equals(JedaiOptions.DIRTY_ER)) {
				
				if (!ec.getEntityIdsD1().isEmpty()) { 
					TIntList duplicate_list = ec.getEntityIdsD1();
					if(duplicate_list.size() > 1 ) {
						List<EntityProfileNode> entity_duplicates = new ArrayList<>();
						for (int i = 0; i < duplicate_list.size(); i++){
							int id = duplicate_list.get(i);
							entity_duplicates.add(new EntityProfileNode(WorkflowManager.profilesD1.get(id), id));
						}

						duplicates.add(entity_duplicates);		
					}
				}
				
			}
			else {
				if (!ec.getEntityIdsD1().isEmpty() && !ec.getEntityIdsD2().isEmpty()) {
					TIntList ids_1 = ec.getEntityIdsD1();
					TIntList ids_2 = ec.getEntityIdsD2();
					List<EntityProfileNode> entity_duplicates = new ArrayList<>();
					for (int i = 0; i < ids_1.size(); i++){
						int id = ids_1.get(i);
						entity_duplicates.add(new EntityProfileNode(WorkflowManager.profilesD1.get(id), id));
					}
					for (int i = 0; i < ids_2.size(); i++){
						int id = ids_2.get(i);
						entity_duplicates.add(new EntityProfileNode(WorkflowManager.profilesD2.get(id), id));
					}
					
					if (entity_duplicates.size() > 1 ) duplicates.add(entity_duplicates);					
				}
			}
		}
		return duplicates;
	}
    
    
    
   
	public static String getEr_mode() {
		return er_mode;
	}

	public static void setEr_mode(String er_mode) {
		WorkflowManager.er_mode = er_mode;
	}

	public static List<EntityProfile> getProfilesD1() {
		return profilesD1;
	}

	public static void setProfilesD1(List<EntityProfile> profilesD1) {
		WorkflowManager.profilesD1 = profilesD1;
	}

	public static List<EntityProfile> getProfilesD2() {
		return profilesD2;
	}

	public static void setProfilesD2(List<EntityProfile> profilesD2) {
		WorkflowManager.profilesD2 = profilesD2;
	}

	public static AbstractDuplicatePropagation getGround_truth() {
		return ground_truth;
	}

	public static void setGround_truth(AbstractDuplicatePropagation ground_truth) {
		WorkflowManager.ground_truth = ground_truth;
	}

	public static ISchemaClustering getSchema_clustering() {
		return schema_clustering;
	}

	public static void setSchema_clustering(ISchemaClustering schema_clustering) {
		WorkflowManager.schema_clustering = schema_clustering;
	}

	public static IBlockProcessing getComparison_cleaning() {
		return comparison_cleaning;
	}

	public static void setComparison_cleaning(IBlockProcessing comparison_cleaning) {
		WorkflowManager.comparison_cleaning = comparison_cleaning;
	}

	public static IEntityMatching getEntity_matching() {
		return entity_matching;
	}

	public static void setEntity_matching(IEntityMatching entity_matching) {
		WorkflowManager.entity_matching = entity_matching;
	}

	public static IEntityClustering getEntity_clustering() {
		return entity_clustering;
	}

	public static void setEntity_clustering(IEntityClustering entity_clustering) {
		WorkflowManager.entity_clustering = entity_clustering;
	}

	public static List<IBlockBuilding> getBlock_building() {
		return block_building;
	}

	public static void setBlock_building(List<IBlockBuilding> block_building) {
		WorkflowManager.block_building = block_building;
	}

	public static List<IBlockProcessing> getBlock_cleaning() {
		return block_cleaning;
	}

	public static void setBlock_cleaning(List<IBlockProcessing> block_cleaning) {
		WorkflowManager.block_cleaning = block_cleaning;
	}

	public static EquivalenceCluster[] getEntityClusters() {
		return entityClusters;
	}

	public static void setEntityClusters(EquivalenceCluster[] entityClusters) {
		WorkflowManager.entityClusters = entityClusters;
	}
	
	
	
	
}
